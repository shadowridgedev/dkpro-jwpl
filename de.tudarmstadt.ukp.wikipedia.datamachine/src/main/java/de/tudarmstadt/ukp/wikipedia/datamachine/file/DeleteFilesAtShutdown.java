/*******************************************************************************
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package de.tudarmstadt.ukp.wikipedia.datamachine.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * A file deletion "watch dog" that can be to remove files via its {@link Path} references. It will clean out files
 * upon JVM shutdown: guaranteed!
 *
 * Inspired by and adapted from the answer here: https://stackoverflow.com/a/42389029
 */
public final class DeleteFilesAtShutdown {
    private static LinkedHashSet<Path> paths = new LinkedHashSet<>();

    static {
        // registers the call of 'shutdownHook' at JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(DeleteFilesAtShutdown::cleanupRegisteredFiles));
    }

    private static void cleanupRegisteredFiles() {
        LinkedHashSet<Path> local;
        synchronized(DeleteFilesAtShutdown.class){
            local = paths;
            paths = null;
        }

        ArrayList<Path> toBeDeleted = new ArrayList<>(local);
        Collections.reverse(toBeDeleted);
        for (Path p : toBeDeleted) {
            try {
                Files.delete(p);
            } catch (IOException | RuntimeException e) {
                // do nothing - best-effort
            }
        }
    }

    /**
     * Registers a {@link Path} to be removed at JVM shutdown.
     * @param filePath A valid path pointing to a file.
     */
    public static synchronized void register(Path filePath) {
        if (paths == null) {
            throw new IllegalStateException("Shutdown hook is already in progress. Adding paths is not allowed now!");
        }
        paths.add(filePath);
    }
}