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

package io.patriot_framework.network_simulator.docker.multihost.container;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import io.patriot_framework.network_simulator.docker.container.DockerContainer;
import io.patriot_framework.network_simulator.docker.manager.DockerManager;
import io.patriot_framework.network_simulator.docker.multihost.cluster.flannel.FlannelEnvironment;
import io.patriot_framework.network_simulator.docker.multihost.cluster.flannel.Subnet;
import io.patriot_framework.network_simulator.docker.parser.EnvironmentFileParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Class representing container running Flannel inside docker hosts
 */
public class FlannelContainer extends DockerContainer {
    /**
     * @param name           Container name
     * @param id             Identifier
     * @param dockerManager  Manager
     */
    public FlannelContainer(String name, String id, DockerManager dockerManager) {
        super(name, id, dockerManager);
    }

    /**
     * Fetches and returns configuration used by flannel
     * @return Configuration generated by flannel
     */
    public FlannelEnvironment getConfiguration() {
        DockerManager manager = (DockerManager) this.getManager();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ExecCreateCmdResponse execCreateCmdResponse = manager.client().execCreateCmd(this.getId())
                .withAttachStdout(true)
                .withPrivileged(true)
                .withCmd("bash", "-c", "cat /run/flannel/subnet.env")
                .exec();
        try {
            manager.client().execStartCmd(execCreateCmdResponse.getId())
                    .exec(new ExecStartResultCallback(stream, stream))
                    .awaitCompletion(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        EnvironmentFileParser env = new EnvironmentFileParser(new ByteArrayInputStream(stream.toByteArray()));
        boolean ipmasq = Boolean.parseBoolean(env.getKey("FLANNEL_IPMASQ"));
        int mtu = Integer.parseInt(env.getKey("FLANNEL_MTU"));
        Subnet network = Subnet.parseSubnet(env.getKey("FLANNEL_NETWORK"));
        Subnet subnet = Subnet.parseSubnet(env.getKey("FLANNEL_SUBNET"));
        return new FlannelEnvironment(network, subnet, mtu, ipmasq);
    }


}
