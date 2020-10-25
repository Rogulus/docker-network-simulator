/*
 * Copyright 2020 Patriot project
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.patriot_framework.network_simulator.docker.multihost.cluster.flannel;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.HostConfig;
import io.patriot_framework.network_simulator.docker.manager.DockerManager;
import io.patriot_framework.network_simulator.docker.multihost.cluster.Cluster;
import io.patriot_framework.network_simulator.docker.multihost.cluster.EtcdCluster;
import io.patriot_framework.network_simulator.docker.multihost.container.FlannelContainer;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Flannel cluster that is deployed across multiple hosts
 */
public class FlannelCluster implements Cluster {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlannelCluster.class);

    private static final String FLANNEL_IMAGE = "quay.io/coreos/flannel:v0.13.1-rc1";

    private final Subnet subnet;
    private final String etcdPrefix;
    private final EtcdCluster etcdCluster;
    private final List<DockerManager> hosts;
    private final String name;
    private final int port;
    private final List<FlannelContainer> containers = new ArrayList<>();
    private boolean deployed = false;

    /**
     * @param hosts        Host that are part of this cluster
     * @param name         Name of this flannel cluster and all the entworks it creates
     * @param etcdCluster  ETCD cluster that is running on all of the hosts
     * @param subnet       Network defining subnet that the flannel should use
     * @param etcdPrefix   Prefix for the ETCD database
     * @param port         Port that this cluster should use for communication
     */
    public FlannelCluster(Collection<DockerManager> hosts, String name, EtcdCluster etcdCluster,
                          Subnet subnet, String etcdPrefix, int port) {
        this.hosts = new ArrayList<>(hosts);
        this.name = name;
        this.subnet = subnet;
        this.etcdPrefix = etcdPrefix;
        this.etcdCluster = etcdCluster;
        this.port = port;
    }

    @Override
    public void deploy() throws IOException {
        try {
            try {
                this.etcdCluster.setValue(this.etcdPrefix + "/config", createConfiguration());
            } catch (IOException | EtcdAuthenticationException | TimeoutException | EtcdException e) {
                e.printStackTrace();
            }
            for (DockerManager host : hosts) {
                deployFlannel(host);
            }
        } catch (Exception e) {
            this.close();
            throw e;
        }
    }

    private void deployFlannel(DockerManager host) {
        try {
            LOGGER.info("Pulling flannel image (" + FLANNEL_IMAGE + ") for host " + host.getHost());
            host.client().pullImageCmd(FLANNEL_IMAGE).start().awaitCompletion();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info("Creating flannel container " + this.name + " for host " + host.getHost());
        CreateContainerResponse result = host.client().createContainerCmd(this.name)
                .withImage(FLANNEL_IMAGE)
                .withHostConfig(new HostConfig()
                        .withNetworkMode("host")
                        .withPrivileged(true))
                .withCmd("-etcd-prefix=" + this.etcdPrefix)
                .withName(this.name)
                .exec();
        FlannelContainer container = new FlannelContainer(this.name, result.getId(), host);
        containers.add(container);
        LOGGER.info("Starting flannel container " + this.name + " for host " + host.getHost());
        container.startContainer();
    }

    private String createConfiguration() {
        return "{\"Network\": \"" + this.subnet.toString() + "\"," +
                " \"Backend\":{ \"Type\": \"vxlan\", \"Port\": " + port + ", \"VNI\": " + port + "}}";
    }

    @Override
    public void close() throws IOException {
        LOGGER.info("Deleting flannel containers");
        for (FlannelContainer c : containers) {
            c.destroyContainer();
        }
        deployed = false;
        containers.clear();
    }

    @Override
    public boolean isDeployed() {
        return this.deployed;
    }

    @Override
    public int nodes() {
        return hosts.size();
    }

    /**
     * @return All containers that are part of this cluster
     */
    public List<FlannelContainer> getContainers() {
        return containers;
    }

}
