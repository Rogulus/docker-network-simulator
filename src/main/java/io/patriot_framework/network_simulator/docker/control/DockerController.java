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

package io.patriot_framework.network_simulator.docker.control;

import io.patriot_framework.network.simulator.api.control.Controller;
import io.patriot_framework.network.simulator.api.model.devices.Device;
import io.patriot_framework.network.simulator.api.model.network.Network;
import io.patriot_framework.network_simulator.docker.container.Container;
import io.patriot_framework.network_simulator.docker.container.DockerContainer;
import io.patriot_framework.network_simulator.docker.manager.DockerManager;
import io.patriot_framework.network_simulator.docker.network.DockerNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Implementation of Controller interface for Docker
 */
public class DockerController implements Controller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerController.class);
    private DockerManager dockerManager;

    /**
     * Constructor
     */
    public DockerController() {
        this.dockerManager = new DockerManager();
    }

    @Override
    public void connectDeviceToNetwork(Device device, Network network) {
        stopDevice(device);
        Container container = getDeviceContainer(device);
        dockerManager.connectContainerToNetwork(container, network);
        dockerManager.startContainer(container);
        device.addAddressForNetwork(dockerManager.findIpAddress(container, network), network.getName());
        LOGGER.info("Container: " + device.getName() + "is connected to network: " + network.getName());
    }

    @Override
    public void connectDeviceToNetwork(Device device, List<Network> networks) {
        for (Network network : networks) {
            dockerManager.connectContainerToNetwork(getDeviceContainer(device), network);
            LOGGER.info("Container: " + device.getName() + "is connected to network: " + network.getName());
        }
    }

    @Override
    public void stopDevice(Device device) {
        LOGGER.info("Stopping device: " + device.getName());
        dockerManager.killContainer(getDeviceContainer(device));
    }

    @Override
    public void disconnectDevice(Device device, Network network) {
        LOGGER.info("Disconnecting device: " + device.getName() + " from network: " + network.getName());
        dockerManager.disconnectContainer(getDeviceContainer(device), network);
    }

    @Override
    public void destroyDevice(Device device) {
        LOGGER.info("Destroying device: " + device.getName());
        stopDevice(device);
        dockerManager.destroyContainer(getDeviceContainer(device));
    }

    @Override
    public void createNetwork(Network network) {
        LOGGER.info("Creating network: " + network.getName());
        DockerNetwork dockerNetwork = (DockerNetwork) dockerManager.createNetwork(network.getName(),
                network.getIPAddress() + "/" + network.getMask());
        network.setId(dockerNetwork.getId());

    }

    @Override
    public void destroyNetwork(Network network) {
        LOGGER.info("Destroying network: " + network.getName());
        dockerManager.destroyNetwork(network);
    }

    @Override
    public void deployDevice(Device device, String tag, List<String> envVars) {
        LOGGER.info("Deploying device: " + device.getName() + " from image tag: " + tag);
        DockerContainer dockerContainer = (DockerContainer) dockerManager.createContainer(device.getName(), tag);
        dockerManager.startContainer(dockerContainer);
        device.setIPAddress(dockerManager.findIpAddress(dockerContainer));

    }

    @Override
    public void deployDevice(Device device, String tag) {
        deployDevice(device, tag, new ArrayList<>());
    }

    @Override
    public void deployDevice(Device device, String tag, String monitoringIP, int monitoringPort, List<String> envVars) {
        LOGGER.info("Deploying device: " + device.getName() + " from image tag: " + tag);
        DockerContainer dockerContainer = (DockerContainer) dockerManager.createContainer(device.getName(),
                tag, monitoringIP, monitoringPort, envVars);
        dockerManager.startContainer(dockerContainer);
        device.setIPAddress(dockerManager.findIpAddress(dockerContainer));
    }

    @Override
    public void deployDevice(Device device, String tag, String monitoringIP, int monitoringPort) {
        deployDevice(device, tag, monitoringIP, monitoringPort, new ArrayList<>());
    }

    @Override
    public void deployDevice(Device device, File file) {
        final String tag = "deviceTag";
        LOGGER.info("Deploying device: " + device.getName() + " from image tag: " + tag);
        buildImage(file, tag);
        DockerContainer dockerContainer =
                (DockerContainer) dockerManager.createContainer(device.getName(), tag);
        device.setIPAddress(dockerManager.findIpAddress(dockerContainer));

    }


    @Override
    public void buildImage(File file, String tag) {
        LOGGER.info("Building image from " + file.getPath() + " with tag: " + tag);
        dockerManager.buildImage(file, new HashSet<>(Collections.singletonList(tag)));
    }

    @Override
    public String findGWNetworkIPAddress(Device device) {
        DockerContainer container = (DockerContainer) getDeviceContainer(device);
        return dockerManager.getDefaultGwNetworkIp(container);
    }

    @Override
    public String findGWIPAddress(Device device) {
        DockerContainer container = (DockerContainer) getDeviceContainer(device);
        return dockerManager.getGatewayIP(container);
    }

    @Override
    public Integer findGWMask(Device device) {
        return dockerManager.getDefaultGwNetworkMask(getDeviceContainer(device));
    }

    @Override
    public String getIdentifier() {
        return "Docker";
    }

    @Override
    public void executeCommand(Device device, String command) {
        dockerManager.runCommand(getDeviceContainer(device), command);
    }

    @Override
    public void startDevice(Device device) {
        dockerManager.startContainer(getDeviceContainer(device));
    }

    /**
     * Finds container from device name. If container is not found returns null.
     *
     * @param device
     * @return Device container
     */
    private Container getDeviceContainer(Device device) {
        List<Container> dockerContainers = dockerManager.listContainers();

        for (Container c : dockerContainers) {
            String cName = c.getName().replace("[/", "");
            cName = cName.replace("]", "");
            if (cName.equals(device.getName())) {
                return c;
            }
        }
        return null;
    }
}
