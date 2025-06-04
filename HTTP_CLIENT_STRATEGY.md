# HTTP Client Strategy for RENDPROG.CBL in a CICS Environment

## 1. Introduction

The `RENDPROG.CBL` program needs to communicate with a Hypernova server, which is typically done via HTTP POST requests. This requires HTTP client functionality within the COBOL program to send job data (likely formatted as JSON) and receive the server's response. This document outlines the strategy for implementing this functionality using CICS web services.

## 2. CICS Web API

In a CICS (Customer Information Control System) environment, COBOL programs can leverage the CICS Web API to act as HTTP clients. This API provides commands to manage HTTP connections, send requests, and receive responses. The primary set of commands for this purpose is `EXEC CICS WEB`.

## 3. Key CICS Commands

The following CICS commands will be central to implementing the HTTP client:

*   **`EXEC CICS WEB OPEN`**
    *   **Purpose:** To initialize an HTTP client session with a specific URI.
    *   **Usage:** This command is used to specify the scheme (HTTP/HTTPS), host, and path of the target Hypernova server. The URL will be taken from `WS-URL` in `RENDPROG.CBL`.
    *   It returns a session token (SESSTOKEN) that must be used in subsequent WEB commands for this session.

*   **`EXEC CICS WEB CONVERSE`**
    *   **Purpose:** To send an HTTP request and receive the corresponding response in a single operation. This is suitable for simpler interactions like a POST request where the entire request body is sent, and the entire response body is received.
    *   **Usage:** This command can be used to perform the POST request. The request body (JSON) would be supplied from `WS-HTTP-REQUEST-BODY`, and the server's response would be placed into `WS-HTTP-RESPONSE-BODY`.
    *   **Alternative (for more control or chunking, though likely not needed for this use case initially):**
        *   **`EXEC CICS WEB SEND`**: To send the HTTP request body (e.g., the JSON payload). This is used if the request needs to be sent in parts or if more control over the sending process is required.
        *   **`EXEC CICS WEB RECEIVE`**: To receive the HTTP response from the server. This is used in conjunction with `WEB SEND` to get the server's reply.

*   **`EXEC CICS WEB CLOSE`**
    *   **Purpose:** To terminate the HTTP client session and release associated resources.
    *   **Usage:** This command should be called after the HTTP interaction is complete, using the SESSTOKEN obtained from `WEB OPEN`.

## 4. RENDPROG.CBL Integration Points

The CICS WEB commands will be integrated into the `PROCEDURE DIVISION` of `RENDPROG.CBL`.

*   **Location:**
    *   A new paragraph or section, for example, `PERFORM-HYPERNOVA-REQUEST`, would be created within the `PROCEDURE DIVISION`. This paragraph would encapsulate the logic for:
        1.  Opening the web connection (`WEB OPEN`).
        2.  Preparing the request body (moving data to `WS-HTTP-REQUEST-BODY` and setting `WS-HTTP-REQUEST-BODY-LEN`).
        3.  Sending the request and receiving the response (`WEB CONVERSE` or `WEB SEND`/`WEB RECEIVE`).
        4.  Closing the web connection (`WEB CLOSE`).
    *   This new paragraph would be called after job data has been prepared in `WS-JOBS-TABLE` and serialized into `WS-HTTP-REQUEST-BODY`.

*   **Working-Storage Variables Integration:**
    *   `WS-URL`: Used in the `URIMAP` or `HOST` and `PATH` options of `EXEC CICS WEB OPEN` (or directly with `WEB CONVERSE` if not using `OPEN` separately, though `OPEN` is good practice for clarity and potential reuse).
    *   `WS-HTTP-REQUEST-BODY`: Used in the `FROM()` option of `EXEC CICS WEB CONVERSE` or `EXEC CICS WEB SEND`. The length of the data in this field will be specified using `WS-HTTP-REQUEST-BODY-LEN` in the `FROMLENGTH()` option.
    *   `WS-HTTP-RESPONSE-BODY`: Used in the `INTO()` option of `EXEC CICS WEB CONVERSE` or `EXEC CICS WEB RECEIVE`. The `MAXLENGTH()` option would be set to the size of `WS-HTTP-RESPONSE-BODY`, and `WS-HTTP-RESPONSE-BODY-LEN` would be updated by CICS with the actual length of the received data.

    **Example Snippet (Conceptual):**
    ```cobol
    PROCEDURE DIVISION.
    MAIN-LOGIC.
        DISPLAY "Renderer Program Stub".
        PERFORM PREPARE-JOB-DATA.
        IF WS-CURRENT-JOB-COUNT > 0 THEN
            PERFORM SERIALIZE-JOBS-TO-JSON
            PERFORM PERFORM-HYPERNOVA-REQUEST
            PERFORM PROCESS-HYPERNOVA-RESPONSE
        END-IF.
        STOP RUN.

    PERFORM-HYPERNOVA-REQUEST.
      * Initialize SESSTOKEN, URIMAP etc.
      * EXEC CICS WEB OPEN URIMAP(myUrimap) SESSTOKEN(ws-sesstoken) ...
      * Check EIBRESP

      * Assuming WS-HTTP-REQUEST-BODY and WS-HTTP-REQUEST-BODY-LEN are populated
      * EXEC CICS WEB CONVERSE METHOD(POST) SESSTOKEN(ws-sesstoken)
      *     FROM(WS-HTTP-REQUEST-BODY)
      *     FROMLENGTH(WS-HTTP-REQUEST-BODY-LEN)
      *     INTO(WS-HTTP-RESPONSE-BODY)
      *     TOLENGTH(WS-HTTP-RESPONSE-BODY-LEN)
      *     MAXLENGTH(LENGTH OF WS-HTTP-RESPONSE-BODY)
      *     MEDIATYPE('application/json')
      * Check EIBRESP

      * EXEC CICS WEB CLOSE SESSTOKEN(ws-sesstoken)
      * Check EIBRESP
      .
    ```

## 5. Error Handling

After every `EXEC CICS` command, the `EIBRESP` field (from the Execute Interface Block - EIB, automatically available to CICS programs) must be checked to determine if the command executed successfully.
*   A value of `DFHRESP(NORMAL)` indicates success.
*   Other values indicate various errors (e.g., `NOTFND`, `INVREQ`, `IOERR`). The program must include logic to handle these error conditions appropriately, perhaps by setting an error message in `WS-TEMP-RESPONSE-DATA` (which copies `RESPREC`) and avoiding further processing of that request.

## 6. Assumptions

*   This strategy assumes that `RENDPROG.CBL` will be compiled (or precompiled by the CICS precompiler) and executed within a CICS environment that is configured for web services.
*   The CICS region must have network access to the Hypernova server.
*   Appropriate CICS resource definitions (e.g., `URIMAP`, `TCPIPSERVICE` if CICS is also acting as a server for other things) might be necessary depending on the specific configuration and how the URL is specified to `WEB OPEN`.
*   The Hypernova server is expecting requests and will send responses in a format that can be handled by the COBOL program (e.g., JSON that can be parsed or whose relevant parts can be extracted).
```
