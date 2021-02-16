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

package io.patriot_framework.network_simulator.docker.multihost.cluster;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.HostConfig;
import io.patriot_framework.network_simulator.docker.container.Container;
import io.patriot_framework.network_simulator.docker.container.DockerContainer;
import io.patriot_framework.network_simulator.docker.manager.DockerManager;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdAuthenticationException;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Class representing ETCD v2 cluster
 * https://etcd.io/
 */
public class EtcdCluster implements Cluster {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdCluster.class);

    private static final String ETCD_IMAGE = "quay.io/coreos/etcd:v2.3.8";
    private static final String DEFAULT_CLUSTER_NAME = "etcd-cluster";

    private final String name;
    private final List<DockerManager> hosts;
    private final List<Container> containers = new ArrayList<>();
    private boolean deployed = false;
    private EtcdClient client;

    /**
     * Creates new ETCD cluster with default name
     *
     * @param hosts Hosts that the cluster should run on
     */
    public EtcdCluster(Collection<DockerManager> hosts) {
        this(hosts, DEFAULT_CLUSTER_NAME);
    }

    /**
     * Creates new ETCD cluster
     *
     * @param hosts Hosts that the cluster should run on
     * @param name  Name of this cluster
     */
    public EtcdCluster(Collection<DockerManager> hosts, String name) {
        this.hosts = new ArrayList<>(hosts);
        this.name = name;
    }

    public void deploy() throws IOException {
        if (deployed) {
            throw new IllegalStateException("ETCD cluster is already running");
        }

        // Generate ClusterURL only once
        String clusterUrls = getClusterURLs();

        try {
            deployEtcds(clusterUrls);

            LOGGER.info("Starting etcd containers");

            for (Container container : containers) {
                container.startContainer();
            }

            this.deployed = true;
        } catch (DockerException e) {
            LOGGER.info("Exception caught. deleting etcd containers");
            this.close();
            throw e;
        }
    }

    private EtcdClient getClient() {
        if (client == null) {
            client = new EtcdClient(getHostURI(hosts.get(0), 2379));
        }
        return client;
    }

    private void deployEtcds(String clusterUrls) {
        for (int i = 0; i < hosts.size(); i++) {
            DockerManager host = hosts.get(i);
            LOGGER.info("Deploying etcd for host " + host.getHost());
            String containerName = "etcd";
            try {
                LOGGER.debug("Pulling etcd image (" + ETCD_IMAGE + ") for host " + host.getHost());
                host.client().pullImageCmd(ETCD_IMAGE).start().awaitCompletion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LOGGER.info("Creating etcd container " + containerName + " for host " + host.getHost());

            CreateContainerResponse result = host.client().createContainerCmd(containerName)
                    .withImage(ETCD_IMAGE)
                    .withHostConfig(new HostConfig().withNetworkMode("host"))
                    .withEnv(generateEnv(i, clusterUrls, host))
                    .withName(containerName)
                    .exec();
            containers.add(new DockerContainer(containerName, result.getId(), host));
            LOGGER.info("Finished etcd deploy for host " + host.getHost());
        }
    }

    @Override
    public void close() throws IOException {
        LOGGER.info("Deleting etcd containers");
        for (Container container : containers) {
            container.destroyContainer();
        }
        deployed = false;
        containers.clear();
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private String getClusterURLs() {
        StringBuilder clusterUrlBuilder = new StringBuilder();
        for (int i = 0; i < hosts.size(); i++) {
            DockerManager host = hosts.get(i);
            clusterUrlBuilder
                    .append(generateName(i))
                    .append("=")
                    .append(getHostURI(host, 2380))
                    .append(",");
        }
        clusterUrlBuilder.setLength(clusterUrlBuilder.length() - 1);
        return clusterUrlBuilder.toString();
    }

    private URI getHostURI(DockerManager host, int port) {
        return UriBuilder.fromUri(host.getHost()).scheme("http").port(port).build();
    }

    private String generateName(int id) {
        return "etcd-" + id;
    }

    private List<String> generateEnv(int id, String clusterUrls, DockerManager host) {
        Map<String, String> envs = new HashMap<>();
        envs.put("ETCD_NAME", generateName(id));
        envs.put("ETCD_LISTEN_CLIENT_URLS", "http://0.0.0.0:2379");
        envs.put("ETCD_LISTEN_PEER_URLS", "http://0.0.0.0:2380");
        envs.put("ETCD_INITIAL_ADVERTISE_PEER_URLS", getHostURI(host, 2380).toString());
        envs.put("ETCD_INITIAL_CLUSTER_TOKEN", this.name);
        envs.put("ETCD_INITIAL_CLUSTER", clusterUrls);
        envs.put("ETCD_INITIAL_CLUSTER_STATE", "new");
        envs.put("ETCD_ADVERTISE_CLIENT_URLS", getHostURI(host, 2379).toString());

        return envMapToString(envs);
    }

    private List<String> envMapToString(Map<String, String> map) {
        List<String> envs = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            envs.add(entry.getKey() + "=" + entry.getValue());
        }
        return envs;
    }

    public boolean isDeployed() {
        return deployed;
    }

    @Override
    public int nodes() {
        return hosts.size();
    }

    public String getName() {
        return name;
    }

    /**
     * Sets value in ETCD
     * @param key   Key
     * @param value Value
     * @throws IOException Exception
     * @throws EtcdAuthenticationException Exception
     * @throws TimeoutException Exception
     * @throws EtcdException Exception
     */
    public void setValue(String key, String value)
            throws IOException, EtcdAuthenticationException, TimeoutException, EtcdException {
        if (!isDeployed()) {
            throw new IllegalStateException("Etcd cluster must be deployed in order to fetch values");
        }

        getClient().put(key, value).send().get();
    }

    /**
     * Return value for key in ETCD
     * @param key Key
     * @return Value associated with the key
     * @throws IOException Exception
     * @throws EtcdAuthenticationException Exception
     * @throws TimeoutException Exception
     * @throws EtcdException Exception
     */
    public String getValue(String key)
            throws IOException, EtcdAuthenticationException, TimeoutException, EtcdException {
        if (!isDeployed()) {
            throw new IllegalStateException("Etcd cluster must be deployed in order to fetch values");
        }

        EtcdKeysResponse response = getClient().get(key).send().get();
        return response.node.value;
    }
}
