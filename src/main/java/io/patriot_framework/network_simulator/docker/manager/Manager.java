/*
 * Copyright 2019 Patriot project
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

package io.patriot_framework.network_simulator.docker.manager;

import io.patriot_framework.network.simulator.api.model.network.Network;
import io.patriot_framework.network_simulator.docker.container.Container;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * The interface Manager.
 */
public interface Manager {
    /**
     * Create container container.
     *
     * @param name the name
     * @param tag  the tag
     * @return the container
     */
    Container createContainer(String name, String tag);

    /**
     * Find ip address string.
     *
     * @param container the container
     * @param network   the network
     * @return the string
     */
    String findIpAddress(Container container, Network network);

    /**
     * Create network network.
     *
     * @param name   the name
     * @param subnet the subnet
     * @return the network
     */
    Network createNetwork(String name, String subnet);

    /**
     * List containers list.
     *
     * @return the list
     */
    List<Container> listContainers();

    /**
     * List networks list.
     *
     * @return the list
     */
    List<Network> listNetworks();

    /**
     * Connect container to network.
     *
     * @param container the container
     * @param network   the network
     */
    void connectContainerToNetwork(Container container, Network network);

    /**
     * Method stops container.
     *
     * @param container the container
     */
    void killContainer(Container container);

    /**
     * Disconnects container from network.
     *
     * @param container the container
     * @param network   the network
     */
    void disconnectContainer(Container container, Network network);

    /**
     * Destroy container.
     *
     * @param container the container
     */
    void destroyContainer(Container container);

    /**
     * Destroy network.
     *
     * @param network the network
     */
    void destroyNetwork(Network network);

    /**
     * Run command.
     *
     * @param container the container
     * @param command   the command
     */
    void runCommand(Container container, String command);

    /**
     * Start container.
     *
     * @param container the container
     */
    void startContainer(Container container);

    /**
     * Build image.
     *
     * @param dockerfile the dockerfile
     * @param tag        the tag
     */
    void buildImage(File dockerfile, Set<String> tag);

    /**
     * Delete image.
     *
     * @param tag the tag
     */
    void deleteImage(String tag);

    /**
     * @return URI of the host running the manager
     */
    URI getHost();
}
