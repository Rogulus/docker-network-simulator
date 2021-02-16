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
 * Configuration of specific Flannel environment
 */
public class FlannelEnvironment {
    private final Subnet network;
    private final Subnet subnet;
    private final int mtu;
    private final boolean ipMasq;

    /**
     * @param network Subnet defining the entire network
     * @param subnet  Subnet defining network used by single host
     * @param mtu     Maximum transmission unit for this network
     * @param ipMasq  True, if network is using IP Masquerade
     */
    public FlannelEnvironment(Subnet network, Subnet subnet, int mtu, boolean ipMasq) {
        this.network = network;
        this.subnet = subnet;
        this.mtu = mtu;
        this.ipMasq = ipMasq;
    }

    /**
     * @return Subnet defining the entire network
     */
    public Subnet getNetwork() {
        return network;
    }

    /**
     * @return Subnet defining network used by single host
     */
    public Subnet getSubnet() {
        return subnet;
    }

    /**
     * @return Maximum transmission unit for this network
     */
    public int getMTU() {
        return mtu;
    }

    /**
     * @return True, if network is using IP Masquerade
     */
    public boolean isIpMasq() {
        return ipMasq;
    }
}
