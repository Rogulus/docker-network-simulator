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

package io.patriot_framework.network_simulator.docker.multihost.network;

import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.model.Network;
import io.patriot_framework.network_simulator.docker.manager.DockerManager;
import io.patriot_framework.network_simulator.docker.manager.Manager;
import io.patriot_framework.network_simulator.docker.multihost.cluster.Cluster;
import io.patriot_framework.network_simulator.docker.multihost.cluster.EtcdCluster;
import io.patriot_framework.network_simulator.docker.multihost.cluster.flannel.FlannelCluster;
import io.patriot_framework.network_simulator.docker.multihost.cluster.flannel.FlannelEnvironment;
import io.patriot_framework.network_simulator.docker.multihost.cluster.flannel.Subnet;
import io.patriot_framework.network_simulator.docker.multihost.container.FlannelContainer;
import io.patriot_framework.network_simulator.docker.network.DockerNetwork;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Class representing Network spanning multiple hosts
 * Implemented using https://github.com/coreos/flannel and https://etcd.io/
 */
public class MultiHostNetworkProvider implements Cluster {
    private final String name;
    private final Map<Manager, MultiHostNetwork> networks = new HashMap<>();

    private final FlannelCluster flannel;
    private EtcdCluster etcd = null;

    /**
     * Deploys the multi-host network with provided etcd
     * @param hosts   List of all hosts
     * @param name    Name of the multi host network
     * @param prefix  Subnet to be used for IP assigment purposes
     * @param etcd    ETCD cluster spanning all hosts
     * @param port    Port which should Flannel use for communication
     * @throws IOException Exception
     */
    public MultiHostNetworkProvider(Collection<DockerManager> hosts, String name,
                                    Subnet prefix, EtcdCluster etcd, int port) throws IOException {
        this.name = name;
        this.flannel = new FlannelCluster(hosts, name + "-flannel", etcd, prefix, name + "/network", port);
    }


    /**
     * Deploys the multi-host network with managed etcd
     * @param hosts    List of all hosts
     * @param name     Name of the multi host network
     * @param prefix   Subnet to be used for IP assigment purposes
     * @param port     Port which should Flannel use for communication
     * @throws IOException Exception
     */
    public MultiHostNetworkProvider(Collection<DockerManager> hosts, String name,
                                    Subnet prefix, int port) throws IOException {
        this.name = name;
        this.etcd = new EtcdCluster(hosts, name);
        etcd.deploy();
        this.flannel = new FlannelCluster(hosts, name + "-flannel", etcd, prefix, name + "/network", port);

    }

    private void createNetworks() {
        int i = 1;
        for (FlannelContainer container : flannel.getContainers()) {
            DockerManager manager = (DockerManager) container.getManager();
            MultiHostNetwork network = createNetwork(container.getConfiguration(), manager, i);
            networks.put(manager, network);
            i++;
        }
    }

    private MultiHostNetwork createNetwork(FlannelEnvironment env, DockerManager host, int i) {
        Map<String, String> options = new HashMap<>();
        options.put("com.docker.network.bridge.enable_ip_masquerade", String.valueOf(env.isIpMasq()));
        options.put("com.docker.network.driver.mtu", String.valueOf(env.getMTU()));
        CreateNetworkResponse response = host.client().createNetworkCmd()
                .withName(name + "-" + i)
                .withIpam(new Network.Ipam()
                        .withConfig(new Network.Ipam.Config().withSubnet(env.getSubnet().toString())))
                .withOptions(options)
                .exec();
        return new MultiHostNetwork(name + "-" + i, response.getId());
    }

    /**
     * Returns multi-host network for specific host
     * @param host      Manager
     * @return Network for that host
     */
    public DockerNetwork getNetwork(Manager host) {
        return networks.get(host);
    }

    @Override
    public void close() throws IOException {
        if (etcd != null) {
            etcd.close();
        }
        flannel.close();
        for (Map.Entry<Manager, MultiHostNetwork> entry : networks.entrySet()) {
            entry.getKey().destroyNetwork(entry.getValue());
        }
    }

    @Override
    public void deploy() throws IOException {
        if (etcd != null && !etcd.isDeployed()) {
            etcd.deploy();
        }
        flannel.deploy();
        createNetworks();
    }

    @Override
    public boolean isDeployed() {
        return flannel.isDeployed();
    }

    @Override
    public int nodes() {
        return flannel.nodes();
    }
}
