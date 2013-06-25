/*
 * Copyright 2009-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jggug.kobo.groovyserv

import org.jggug.kobo.groovyserv.exception.GServIllegalStateException
import org.jggug.kobo.groovyserv.exception.InvalidRequestHeaderException
import org.jggug.kobo.groovyserv.platform.CurrentDirHolder
import org.jggug.kobo.groovyserv.platform.EnvironmentVariables
import org.jggug.kobo.groovyserv.utils.DebugUtils

/**
 * @author NAKANO Yasuharu
 */
class GroovyInvokeHandler implements Runnable {

    final CLASSPATH_OPTIONS = ["--classpath", "-cp", "-classpath"]

    private String id
    private InvocationRequest request

    GroovyInvokeHandler(request) {
        this.id = "GroovyInvokeHandler:${request.port}"
        this.request = request
    }

    /**
     * @throws SystemExitException
     *              When user code called System.exit().
     *              Actually this exception is wrapped by ExecutionException.
     * @throws InvalidRequestHeaderException
     *              When classpath option is invalid.
     *              Actually this exception is wrapped by ExecutionException.
     * @throws GServIllegalStateException
     *              When changed current directory after set different directory by another session
     */
    @Override
    void run() {
        Thread.currentThread().name = id
        DebugUtils.verboseLog("${id}: Thread started")
        boolean shouldResetCurrentDir = true
        try {
            CurrentDirHolder.instance.setDir(request.cwd)
            setupEnvVars(request.envVars)
            def classpath = removeClasspathFromArgs(request)
            invokeGroovy(request.args, classpath)
            awaitAllSubThreads()
        }
        catch (InterruptedException e) {
            DebugUtils.verboseLog("${id}: Thread interrupted: ${e.message}")
        }
        catch (GServIllegalStateException e) {
            shouldResetCurrentDir = false
            throw e
        }
        finally {
            killAllSubThreadsIfExist()
            if (shouldResetCurrentDir) {
                // only if not throwing any exception
                CurrentDirHolder.instance.reset()
            }
            DebugUtils.verboseLog("${id}: Thread is dead")
        }
    }

    @Override
    String toString() { id }

    private void setupEnvVars(List<String> envVars) {
        envVars.each { envVar ->
            DebugUtils.verboseLog("${id}: putenv(${envVar})")
            EnvironmentVariables.instance.put(envVar)
        }
    }

    /**
     * Setting a classpath using the -cp or -classpath option means not to use the global classpath.
     * GroovyServ behaves then the same as the java interpreter and Groovy.
     */
    private removeClasspathFromArgs(request) {
        def paths = [] as LinkedHashSet

        // parse classpath option's values from arguments.
        def filteredArgs = [] // args except options about classpath
        for (def it = request.args.iterator(); it.hasNext();) {
            String opt = it.next()
            if (CLASSPATH_OPTIONS.contains(opt)) {
                if (!it.hasNext()) {
                    throw new InvalidRequestHeaderException("${id}: Invalid classpath option: ${request.args}")
                }
                paths += it.next().split(File.pathSeparator) as List
            } else {
                filteredArgs << opt
            }
        }

        // if no classpath option, using Cp header from CLASSPATH environment variable on client.
        if (paths.empty && request.classpath) {
            paths = request.classpath.split(File.pathSeparator) as List
        }

        // CWD must be always the last entry of classpath
        paths << "."

        // removed classpath options
        request.args = filteredArgs

        return paths.join(File.pathSeparator)
    }

    private invokeGroovy(args, classpath) {
        DebugUtils.verboseLog("${id}: Invoking groovy: ${args} with classpath=${classpath}")
        GroovyMain2.processArgs(args as String[], System.out, classpath)
        appendServerVersion(args)
    }

    private appendServerVersion(args) {
        if (args.any { it.startsWith("-v") } || args.contains("--version")) {
            println "GroovyServ Version: Server: @GROOVYSERV_VERSION@"
        }
    }

    private awaitAllSubThreads() {
        def threads = getAllAliveSubThreads()
        if (!threads) {
            DebugUtils.verboseLog("${id}: Threre is no sub thread")
            return
        }
        DebugUtils.verboseLog("${id}: All sub threads are joining....")
        threads.each { thread ->
            if (!thread.isDaemon()) thread.join() // waiting infinitely as original groovy command
        }
        DebugUtils.verboseLog("${id}: All sub threads joined")
    }

    private killAllSubThreadsIfExist() {
        def threads = getAllAliveSubThreads()
        if (!threads) {
            return
        }
        threads.each { thread ->
            thread.stop() // by force
        }
        DebugUtils.verboseLog("${id}: All sub threads stopped by force")
    }

    private getAllAliveSubThreads() { // TODO
        def threadGroup = Thread.currentThread().threadGroup
        Thread[] threads = new Thread[threadGroup.activeCount() + 1] // need at lease one extra space (see Javadoc of ThreadGroup)
        int count = threadGroup.enumerate(threads)
        if (count < threads.size()) {
            // convert to list for convenience except own thread
            def list = (threads as List).findAll { it && it != Thread.currentThread() }
            DebugUtils.verboseLog("${id}: Found ${list.size()} sub thread(s): ${list}")
            return list
        }
        return getAllAliveSubThreads()
    }

}

