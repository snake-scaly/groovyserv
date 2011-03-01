/*
 * Copyright 2009-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


/**
 * Protocol summary:
 * <pre>
 * Request ::= InvocationRequest
 *             ( StreamRequest ) *
 * Response ::= ( StreamResponse ) *
 *
 * InvocationRequest ::=
 *    'Cwd:' <cwd> LF
 *    'Arg:' <arg1> LF
 *    'Arg:' <arg2> LF
 *    'Arg:' <arg3> LF
 *      :
 *    'Env:' <env1>=<value1> LF
 *    'Env:' <env2>=<value2> LF
 *    'Env:' <env3>=<value3> LF
 *      :
 *    'Cp:' <classpath> LF
 *    'Cookie:' <cookie> LF
 *    LF
 *
 *   where:
 *     <cwd> is current working directory.
 *     <arg1>,<arg2>.. are commandline arguments(optional).
 *     <env1>,<env2>.. are environment variable names which sent to the server(optional).
 *     <value1>,<valeu2>.. are environment variable values which sent to
 *                         the server(optional).
 *     <classpath> is the value of environment variable CLASSPATH(optional).
 *     <cookie> is authentication value which certify client is the user who
 *              invoked the server.
 *     LF is line feed (0x0a, '\n').
 *
 * StreamRequest ::=
 *    'Size:' <size> LF
 *    LF
 *    <data from STDIN>
 *
 *   where:
 *     <size> is the size of data to send to server.
 *            <size>==-1 means client exited.
 *     <data from STDIN> is byte sequence from standard input.
 *
 * StreamResponse ::=
 *    'Status:' <status> LF
 *    'Channel:' <id> LF
 *    'Size:' <size> LF
 *    LF
 *    <data for STDERR/STDOUT>
 *
 *   where:
 *     <status> is exit status of invoked groovy script.
 *     <id> is 'out' or 'err', where 'out' means standard output of the program.
 *          'err' means standard error of the program.
 *     <size> is the size of chunk.
 *     <data from STDERR/STDOUT> is byte sequence from standard output/error.
 *
 * </pre>
 *
 * @author UEHARA Junji
 * @author NAKANO Yasuharu
 */
class ClientProtocols {

    private final static String HEADER_CURRENT_WORKING_DIR = "Cwd"
    private final static String HEADER_ARG = "Arg"
    private final static String HEADER_CP = "Cp"
    private final static String HEADER_STATUS = "Status"
    private final static String HEADER_COOKIE = "Cookie"
    private final static String HEADER_STREAM_ID = "Channel"
    private final static String HEADER_SIZE = "Size"
    private final static String HEADER_ENV = "Env"
    private final static String LINE_SEPARATOR = "\n"

    /**
     * @throws InvalidRequestHeaderException
     * @throws GServIOException
     */
    static InvocationRequest readInvocationRequest(ClientConnection conn) {
        Map<String, List<String>> headers = readHeaders(conn)
        def request = new InvocationRequest(
            port: conn.socket.port,
            cwd: headers[HEADER_CURRENT_WORKING_DIR][0],
            classpath: headers[HEADER_CP]?.getAt(0),
            args: headers[HEADER_ARG],
            clientCookie: headers[HEADER_COOKIE]?.getAt(0),
            serverCookie: conn.cookie,
            envVars: headers[HEADER_ENV]
        )
        request.check()
        return request
    }

    /**
     * @throws InvalidRequestHeaderException
     * @throws GServIOException
     */
    static StreamRequest readStreamRequest(ClientConnection conn) {
        Map<String, List<String>> headers = readHeaders(conn)
        def request = new StreamRequest(
            port: conn.socket.port,
            size: headers[HEADER_SIZE]?.getAt(0)
        )
        return request
    }

    private static Map<String, List<String>> readHeaders(ClientConnection conn) {
        def id = "RequestHeader:${conn.socket.port}"
        def ins = conn.socket.inputStream // raw strem
        return parseHeaders(id, ins)
    }

    private static Map<String, List<String>> parseHeaders(String id, InputStream ins) {
        try {
            def headers = [:]
            IOUtils.readLines(ins).each { line ->
                def tokens = line.split(':', 2)
                if (tokens.size() != 2) {
                    throw new InvalidRequestHeaderException("${id}: Found invalid header line: ${line}")
                }
                def (key, value) = tokens
                headers.get(key, []) << value.trim()
            }
            DebugUtils.verboseLog "${id}: Parsed headers: ${headers}"
            return headers
        }
        catch (InterruptedIOException e) {
            throw new GServIOException("${id}: I/O interrupted: interrupted while reading line", e)
        }
        catch (IOException e) {
            throw new GServIOException("${id}: I/O error: failed to read line: ${e.message}", e)
        }
    }

    static byte[] formatAsResponseHeader(streamId, size) {
        def header = [:]
        header[HEADER_STREAM_ID] = streamId
        header[HEADER_SIZE] = size
        formatAsHeader(header)
    }

    static byte[] formatAsExitHeader(status) {
        def header = [:]
        header[HEADER_STATUS] = status
        formatAsHeader(header)
    }

    private static byte[] formatAsHeader(map) {
        def buff = new StringBuilder()
        map.each { key, value ->
            if (key) {
                buff << "$key: $value" << LINE_SEPARATOR
            }
        }
        buff << LINE_SEPARATOR
        buff.toString().bytes
    }

}
