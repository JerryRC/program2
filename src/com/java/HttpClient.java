package com.java;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;


public class HttpClient {

    private static final int buffer_size = 8192;

    private byte[] buffer;

    Socket socket = null;

    private static final int PORT = 80;

    BufferedOutputStream OStream = null;

    BufferedInputStream IStream = null;

    private final StringBuffer header;

    private final StringBuffer response;

    static private final String CRLF = "\r\n";

    public HttpClient() {
        buffer = new byte[buffer_size];
        header = new StringBuffer();
        response = new StringBuffer();
    }

    /**
     * <em>connect</em> connects to the input host on the default http port --
     * port 80. This function opens the socket and creates the input and output
     * streams used for communication.
     */
    public void connect(String host) throws Exception {

        socket = new Socket(host, PORT);

        OStream = new BufferedOutputStream(socket.getOutputStream());

        IStream = new BufferedInputStream(socket.getInputStream());
    }

    public void processGetRequest(String request) throws Exception {
        request += CRLF + CRLF;
        buffer = request.getBytes();
        OStream.write(buffer, 0, request.length());
        OStream.flush();
        processResponse();
    }

    public void processPutRequest(String request) throws Exception {
        //=======start your job here============//
        StringTokenizer st = new StringTokenizer(request, " ");
        String des;
        //提取文件路径
        if (st.nextToken().equals("PUT")) {
            des = st.nextToken();
        } else {
            throw new Exception("Illegal instruction!");
        }
        //根据题意，目前只处理了相对路径的命令
        File file = new File(System.getProperty("user.dir") + "/" + des);
        if (!file.isFile()) {
            throw new Exception("Not File!");
        }

        request += CRLF;
        request += "Content-Length:" + file.length();
        request += CRLF + CRLF;
        System.out.println(request);
        buffer = request.getBytes();
        OStream.write(buffer, 0, request.length());
        OStream.flush();

        processResponse();
        //=======end of your job============//
    }

    public void processResponse() throws Exception {
//        header.delete(0, header.length());
//        response.delete(0, response.length());
        int last = 0, c;
        boolean inHeader = true; // loop control
        while (inHeader && ((c = IStream.read()) != -1)) {
            switch (c) {
                case '\r':
                    break;
                case '\n':
                    if (c == last) {
                        inHeader = false;
                        break;
                    }
                    last = c;
                    header.append("\n");
                    break;
                default:
                    last = c;
                    header.append((char) c);
            }
        }

        while (IStream.read(buffer) != -1) {
            response.append(new String(buffer, StandardCharsets.ISO_8859_1));
        }
    }

    public String getHeader() {
        return header.toString();
    }

    public String getResponse() {
        return response.toString();
    }

    public void close() throws Exception {
        socket.close();
        IStream.close();
        OStream.close();
    }
}
