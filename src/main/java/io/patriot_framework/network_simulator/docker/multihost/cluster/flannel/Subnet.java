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

/**
 * Class representing part of the network in CIDR format
 */
public class Subnet {
    private final int cidr;
    private final String ip;

    /**
     * @param ip    IP Address part to use, e.g. 192.168.0.0
     * @param cidr  Integer value of 1-31. representing number of bits that can differ
     */
    public Subnet(String ip, int cidr) {
        if (cidr < 1 || cidr > 31) {
            throw new IllegalArgumentException("CIDR must be between 1 and 31");
        }
        this.cidr = cidr;
        this.ip = ip;
    }

    @Override
    public String toString() {
        return ip + "/" + cidr;
    }

    /**
     * Parses Subnet from String value
     * @param value Subnet in string value like 192.168.0.0/16
     * @return Subnet
     */
    public static Subnet parseSubnet(String value) {
        String[] parts = value.split("/");
        return new Subnet(parts[0], Integer.parseInt(parts[1]));
    }
}
