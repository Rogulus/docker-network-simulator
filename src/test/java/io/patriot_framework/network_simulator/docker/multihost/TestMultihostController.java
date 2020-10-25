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

package io.patriot_framework.network_simulator.docker.multihost;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import io.patriot_framework.network_simulator.docker.container.DockerContainer;
import io.patriot_framework.network_simulator.docker.manager.DockerManager;
import io.patriot_framework.network_simulator.docker.multihost.cluster.EtcdCluster;
import io.patriot_framework.network_simulator.docker.multihost.cluster.flannel.Subnet;
import io.patriot_framework.network_simulator.docker.multihost.network.MultiHostNetworkProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;


/**
 * This is a test that checks if the multihost network can be successfully deployed
 * and if the created container see each other.
 * It is not a standard test as this requires code changes to set the hosts, so it is a main class with asserts.
 * If successful it will return 0 status code, if it fails it throws an assertion error.
 */
public class TestMultihostController {

    private static final String NETWORK_TEST_IMAGE = "praqma/network-multitool:latest";
    private static final Predicate<String> SUCCESSFUL = Pattern.compile("4 packets transmitted").asPredicate();
    private static final DockerManager[] managers = {
            getHost("tcp://10.0.97.221:8888"),
            getHost("tcp://10.0.97.164:8888"),
            getHost("tcp://10.0.98.65:8888")
    };

    public static void main(String[] args) throws IOException, InterruptedException {
        List<DockerManager> hosts = Arrays.asList(managers);
        Map<DockerContainer, DockerManager> managers = new HashMap<>();
        Map<DockerContainer, String> networks = new HashMap<>();

        // Deploy cluster
        try(MultiHostNetworkProvider allHostsController = new MultiHostNetworkProvider(hosts,
                "all-hosts",
                new Subnet("10.58.0.0", 16),
                8472)) {
                allHostsController.deploy();

                // Create ping containers
                for (DockerManager host: hosts) {
                    DockerContainer container = createPingContainer(host, "ping");
                    attachNetwork(container, allHostsController);
                    container.startContainer();
                    InspectContainerResponse response = host.client().inspectContainerCmd(container.getId()).exec();
                    String ipAddress = response.getNetworkSettings().getNetworks().get(
                            allHostsController.getNetwork(host).getName()
                    ).getIpAddress();
                    networks.put(container, ipAddress);
                    managers.put(container, host);
                }

                for (DockerContainer container : networks.keySet()) {
                    for (String ip : networks.values()) {
                        // Execute command and return output
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        DockerManager manager = managers.get(container);
                        ExecCreateCmdResponse execCreateCmdResponse = manager.client().execCreateCmd(container.getId())
                                .withAttachStdout(true)
                                .withCmd("ping", "-c 4", ip)
                                .exec();

                        manager.client().execStartCmd(execCreateCmdResponse.getId())
                                .exec(new ExecStartResultCallback(stream, stream))
                                .awaitCompletion(10, TimeUnit.SECONDS);

                        // Test that output of a ping command contains 4 successful packets
                        assert SUCCESSFUL.test(stream.toString());
                    }
                }
                // Clean ping containers
                for (DockerContainer container : networks.keySet()) {
                    container.destroyContainer();
                }
        }
    }

    /**
     * @param url URL to the publicly exposed Docker API of the host
     * @return DockerManager representing said host
     */
    private static DockerManager getHost(String url) {
        return new DockerManager(DefaultDockerClientConfig
                .createDefaultConfigBuilder()
                .withDockerHost(url)
                .build());
    }

    /**
     * Creates container with a single purpose to ping
     *
     * @param host DockerManager on which the container will be created
     * @param name Name of the container
     * @return Created container
     */
    private static DockerContainer createPingContainer(DockerManager host, String name) {
        CreateContainerResponse response = host.client().createContainerCmd(name)
                .withImage(NETWORK_TEST_IMAGE)
                .withName(name)
                .exec();
        return new DockerContainer(name, response.getId(), host);
    }

    private static void attachNetwork(DockerContainer container, MultiHostNetworkProvider provider) {
        container.connectToNetwork(Collections.singletonList(provider.getNetwork(container.getManager())));
    }
}
