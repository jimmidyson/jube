/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.jube.process.support;

import java.io.File;

import io.fabric8.jube.process.InstallContext;
import io.fabric8.jube.process.InstallTask;
import io.fabric8.jube.process.config.ProcessConfig;
import io.fabric8.utils.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes one or more files or directories from the distro after unpacking
 */
public class RemoveFilesTask implements InstallTask {
    private static final transient Logger LOG = LoggerFactory.getLogger(RemoveFilesTask.class);
    private final String[] removePaths;

    public RemoveFilesTask(String[] removePaths) {
        this.removePaths = removePaths;
    }

    @Override
    public void install(InstallContext installContext, ProcessConfig config, String id, File installDir) throws Exception {
        if (removePaths != null) {
            File baseDir = ProcessUtils.findInstallDir(installDir);
            for (String removePath : removePaths) {
                File removeFile = new File(baseDir, removePath);
                if (removeFile.exists()) {
                    LOG.info("Removing file " + removeFile);
                    Files.recursiveDelete(removeFile);
                } else {
                    LOG.warn("Post unpack removal path " + removePath + " does not exist at " + removeFile);
                }
            }
        }
    }
}
