/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jimagezip.local;

import io.fabric8.common.util.Objects;
import io.fabric8.common.util.Strings;
import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ControllerDesiredState;
import io.fabric8.kubernetes.api.model.CurrentState;
import io.fabric8.kubernetes.api.model.DesiredState;
import io.fabric8.kubernetes.api.model.Manifest;
import io.fabric8.kubernetes.api.model.ManifestContainer;
import io.fabric8.kubernetes.api.model.PodCurrentContainerInfo;
import io.fabric8.kubernetes.api.model.PodSchema;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.kubernetes.api.model.PodTemplateDesiredState;
import io.fabric8.kubernetes.api.model.ReplicationControllerSchema;
import io.fabric8.kubernetes.api.model.Running;
import io.fabric8.kubernetes.api.model.State;
import io.hawt.aether.OpenMavenURL;
import io.jimagezip.process.InstallOptions;
import io.jimagezip.process.Installation;
import io.jimagezip.process.ProcessController;
import io.jimagezip.process.ProcessManager;
import io.jimagezip.util.ImageMavenCoords;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A set of helper functions for implementing the local node
 */
public class NodeHelper {

    /**
     * Returns the desired state; lazily creating one if required
     */
    public static DesiredState getOrCreateDesiredState(PodSchema pod) {
        Objects.notNull(pod, "pod");
        DesiredState desiredState = pod.getDesiredState();
        if (desiredState == null) {
            desiredState = new DesiredState();
            pod.setDesiredState(desiredState);
        }
        return desiredState;
    }

    /**
     * Returns the current state of the given pod; lazily creating one if required
     */
    public static CurrentState getOrCreateCurrentState(PodSchema pod) {
        Objects.notNull(pod, "pod");
        CurrentState currentState = pod.getCurrentState();
        if (currentState == null) {
            currentState = new CurrentState();
            pod.setCurrentState(currentState);
        }
        return currentState;
    }

    public static PodTemplateDesiredState getPodTemplateDesiredState(ReplicationControllerSchema replicationController) {
        if (replicationController != null) {
            return getPodTemplateDesiredState(replicationController.getDesiredState());
        }
        return null;
    }

    public static PodTemplateDesiredState getPodTemplateDesiredState(ControllerDesiredState desiredState) {
        PodTemplate podTemplate = null;
        PodTemplateDesiredState podTemplateDesiredState = null;
        if (desiredState != null) {
            podTemplate = desiredState.getPodTemplate();
            if (podTemplate != null) {
                podTemplateDesiredState = podTemplate.getDesiredState();
            }
        }
        return podTemplateDesiredState;
    }

    /**
     * Returns the current container map for the current pod state; lazily creating if required
     */
    public static Map<String, PodCurrentContainerInfo> getOrCreateCurrentContainerInfo(PodSchema pod) {
        CurrentState currentState = getOrCreateCurrentState(pod);
        Map<String, PodCurrentContainerInfo> info = currentState.getInfo();
        if (info == null) {
            info = new HashMap<>();
            currentState.setInfo(info);
        }
        return info;
    }

    /**
     * Returns the containers state, lazily creating any objects if required.
     */
    public static State getOrCreateContainerState(PodSchema pod, String containerName) {
        PodCurrentContainerInfo containerInfo = getOrCreateContainerInfo(pod, containerName);
        State state = containerInfo.getState();
        if (state == null) {
            state = new State();
            containerInfo.setState(state);
        }
        return state;
    }

    /**
     * Returns the container information for the given pod and container name, lazily creating as required
     */
    public static PodCurrentContainerInfo getOrCreateContainerInfo(PodSchema pod, String containerName) {
        Map<String, PodCurrentContainerInfo> map = getOrCreateCurrentContainerInfo(pod);
        PodCurrentContainerInfo containerInfo = map.get(containerName);
        if (containerInfo == null) {
            containerInfo = new PodCurrentContainerInfo();
            map.put(containerName, containerInfo);
        }
        return containerInfo;
    }

    /**
     * Creates any missing containers; updating the currentState with the new values.
     */
    public static String createMissingContainers(ProcessManager processManager, PodSchema pod, CurrentState currentState, List<ManifestContainer> containers) throws Exception {
        Map<String, PodCurrentContainerInfo> currentContainers = KubernetesHelper.getCurrentContainers(currentState);

        for (ManifestContainer container : containers) {
            // TODO check if we already have a working container
            createContainer(processManager, container, pod, currentState);
        }
        return null;
    }

    protected static void createContainer(ProcessManager processManager, ManifestContainer container, PodSchema pod, CurrentState currentState) throws Exception {
        String containerName = container.getName();
        String image = container.getImage();
        Strings.notEmpty(image);
        OpenMavenURL mavenUrl = ImageMavenCoords.dockerImageToMavenURL(image);
        Objects.notNull(mavenUrl, "mavenUrl");

        System.out.println("Creating new container from: " + mavenUrl);
        InstallOptions.InstallOptionsBuilder builder = new InstallOptions.InstallOptionsBuilder().url(mavenUrl);
        if (Strings.isNotBlank(containerName)) {
            builder = builder.name(containerName).id(containerName);
        }
        InstallOptions installOptions = builder.build();

        Installation installation = processManager.install(installOptions, null);
        File installDir = installation.getInstallDir();

        PodCurrentContainerInfo containerInfo = NodeHelper.getOrCreateContainerInfo(pod, containerName);

        System.out.println("Installed new process at: " + installDir);

        // TODO add a container to the current state
        ProcessController controller = installation.getController();
        controller.start();

        Long pid = controller.getPid();
        containerAlive(pod, containerName, pid != null && pid.longValue() > 0);

        System.out.println("Started the process!");
    }

    public static void containerAlive(PodSchema pod, String id, boolean alive) {
         CurrentState currentState = getOrCreateCurrentState(pod);
         if (alive) {
             currentState.setStatus("Running");
         } else {
             currentState.setStatus("Waiting");
         }
         State state = getOrCreateContainerState(pod, id);
         if (alive) {
             Running running = new Running();
             state.setRunning(running);
         } else {
             state.setRunning(null);
         }
     }

    public static ManifestContainer addOrUpdateDesiredContainer(PodSchema pod, ManifestContainer container) {
        DesiredState podDesiredState = NodeHelper.getOrCreateDesiredState(pod);
        Manifest manifest = podDesiredState.getManifest();
        if (manifest == null) {
            manifest = new Manifest();
            podDesiredState.setManifest(manifest);
        }
        List<ManifestContainer> containers = manifest.getContainers();
        if (containers == null) {
            containers = new ArrayList<>();
            manifest.setContainers(containers);
        }
        ManifestContainer oldContainer = findContainer(containers, container.getName());
        if (oldContainer != null) {
            // lets update it just in case something changed...
            containers.remove(oldContainer);
        }
        containers.add(container);
        return container;
    }

    public static ManifestContainer findContainer(List<ManifestContainer> containers, String name) {
        for (ManifestContainer container : containers) {
            if (Objects.equal(container.getName(), name)) {
                return container;
            }
        }
        return null;
    }
}
