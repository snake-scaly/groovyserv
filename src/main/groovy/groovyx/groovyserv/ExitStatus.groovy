/*
 * Copyright 2009 the original author or authors.
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
package groovyx.groovyserv

/**
 * @author NAKANO Yasuharu
 */
public enum ExitStatus {

    SUCCESS(0),
    UNEXPECTED_ERROR(1),
    INVALID_REQUEST(2),
    IO_ERROR(3),
    TERMINATED(5),
    INTERRUPTED(6),
    FORCELY_SHUTDOWN(7),
    // NOTE: no change as follows because it's used from a user command
    INVALID_AUTHTOKEN(201),
    CLIENT_NOT_ALLOWED(202),
    CURRENT_DIR_CONFLICTED(203),
    ILLEGAL_STATE(204)

    int code

    private ExitStatus(int code) {
        this.code = code
    }
}

