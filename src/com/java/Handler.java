package com.java;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

/**
 * 辅助 Handler 每个线程的实际策略
 *
 * @author JR Chan
 */
public class Handler implements Runnable {
    private final Socket socket;            //线程池传过来的参数Socket
    private final String currentPath;     //每次新连接都跳转到服务器指定目录

    BufferedOutputStream OStream = null;
    BufferedInputStream IStream = null;

    private static final int buffer_size = 8192;
    private byte[] buffer;

    private final StringBuffer header;
    private final StringBuffer body;

    static private final String CRLF = "\r\n";

    /**
     * 构造函数 创建 Handler
     *
     * @param cwd    当前工作路径
     * @param socket 线程池传过来的Tcp socket
     * @throws SocketException socket错误
     */
    public Handler(final String cwd, Socket socket) throws IOException {
        this.currentPath = cwd;
        this.socket = socket;
        System.out.println("<" + socket.getInetAddress() + "："
                + socket.getPort() + "> 连接成功");

        buffer = new byte[buffer_size];
        header = new StringBuffer();
        body = new StringBuffer();

        initStream();   //初始化流
    }

    /**
     * 初始化输入输出流对象方法
     *
     * @throws IOException 流错误
     */
    public void initStream() throws IOException {
        //输出流，向客户端写信息
        OStream = new BufferedOutputStream(socket.getOutputStream());
        //输入流，读取客户端信息
        IStream = new BufferedInputStream(socket.getInputStream());
    }

    /**
     * 每个线程的实际执行策略
     */
    @Override
    public void run() {
        try {
            processResponse();

            System.out.println("header: " + header.toString()); //输出头部
            System.out.println("body: " + body.toString()); //输出消息体

            StringTokenizer st = new StringTokenizer(header.toString(), " ");
            String command = st.nextToken();    //第一个分段为指令名

            String response;
            boolean BAD = true;
            //GET请求
            if (command.equals("GET")) {
                String filename = st.nextToken();   //第二个分段就是文件路径
                //第三个分段若不是 HTTP/1.0 则 bad request
                if (st.nextToken().trim().equals("HTTP/1.0")) {
                    //格式正确
                    BAD = false;
                    String result = getFile(filename, currentPath);
                    //没有这个文件
                    if (result.equals("unknown file")) {
                        response = "HTTP/1.0 404 Not Found" + CRLF;
                        response += "Server: MyHttpServer/1.0";
                        response += CRLF + CRLF;
                        buffer = response.getBytes();
                        OStream.write(buffer, 0, response.length());
                        OStream.flush();
                        //刷新buffer
                        buffer = new byte[buffer_size];
                    }
                    //200 返回指定文件
                    else {
                        //文件转换成 byte[] 输出
                        File f = new File(result);
                        FileInputStream fis = new FileInputStream(f);

                        String type = "application/octet-stream";
                        if (result.contains(".htm")) {
                            type = "text/html";
                        } else if (result.endsWith(".jpg") || result.endsWith(".jpeg")) {
                            type = "image/jpeg";
                        } else if (result.endsWith(".png")) {
                            type = "image/png";
                        } else if (result.endsWith(".gif")) {
                            type = "image/gif";
                        } else if (result.endsWith(".txt")) {
                            type = "text/plain";
                        }

                        response = "HTTP/1.0 200 OK" + CRLF;
                        response += "Content-Length: " + f.length() + CRLF;
                        response += "Content-Type: " + type + CRLF;
                        response += "Server: MyHttpServer/1.0";
                        response += CRLF + CRLF;

                        buffer = response.getBytes();
                        OStream.write(buffer, 0, response.length());
                        OStream.flush();
                        //刷新buffer
                        buffer = new byte[buffer_size];

                        int size;
                        while ((size = fis.read(buffer)) != -1) {
                            OStream.write(buffer, 0, size);
                            OStream.flush();
                            //刷新buffer
                            buffer = new byte[buffer_size];
                        }
                        fis.close();
                    }
                }
            }
            //PUT请求
            else if (command.equals("PUT")) {
                String filename = st.nextToken();   //第二个分段就是文件路径
                if (st.nextToken().trim().equals("HTTP/1.0")) {
                    //格式正确
                    BAD = false;
                    //取文件名
                    int start = Math.max(
                            filename.lastIndexOf("\\"), filename.lastIndexOf("/"));
                    filename = filename.substring(start + 1);
                    //创建文件
                    FileOutputStream fileOut = new FileOutputStream(currentPath + "/" + filename);
                    fileOut.write(body.toString().getBytes(StandardCharsets.ISO_8859_1));
                    fileOut.flush();
                    fileOut.close();

                    response = "HTTP/1.0 201 Created" + CRLF;
                    response += "Server: MyHttpServer/1.0" + CRLF;
                    response += "Content-type: text/html";
                    response += CRLF + CRLF;
                    buffer = response.getBytes();
                    OStream.write(buffer, 0, response.length());
                    OStream.flush();
                    //刷新buffer
                    buffer = new byte[buffer_size];
                }
            }
            //其他指令
            if (BAD) {
                response = "HTTP/1.0 400 Bad Request" + CRLF;
                response += "Server: MyHttpServer/1.0";
                response += CRLF + CRLF;
                buffer = response.getBytes();
                OStream.write(buffer, 0, response.length());
                OStream.flush();
                //刷新buffer
                buffer = new byte[buffer_size];
            }
            //关闭流，客户端才会-1退出
            OStream.close();
        }
        //避免异常后直接结束服务器端
        catch (IOException e) {
            System.out.println("connection break");
        }
    }

    /**
     * 处理 get 命令
     *
     * @param filename    目标文件名
     * @param currentPath 当前工作目录
     * @return 文件获取情况
     * @throws IOException null
     */
    private String getFile(String filename, String currentPath) throws IOException {
        //处理相对路径命令  处理正反斜杠 多级跳转等问题  ..\..   \\..  ./
        File file = new File(currentPath + "\\" + filename);

        if (file.isFile()) {  //是文件的话返回绝对路径
            return file.getCanonicalPath();
        } else {  //不是文件
            return "unknown file";
        }
    }

    /**
     * 用于处理 http 报文
     * 为 header 和 body 赋值
     *
     * @throws IOException 各种错误
     */
    public void processResponse() throws IOException {
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

        if (header.toString().startsWith("PUT")) {
            //找到文件长度
            int start = header.toString().indexOf("Content-Length:");
            int end = header.toString().indexOf("\n", start);
            int len = Integer.parseInt(header.toString().substring(start + 15, end).trim());

            int times = (len + buffer_size - 1) / buffer_size;    //向上取整

            for (int i = 0; i < times-1 && IStream.read(buffer) != -1; ++i) {

                body.append(new String(buffer, StandardCharsets.ISO_8859_1));
                buffer = new byte[buffer_size];
            }
            int size = IStream.read(buffer);
            body.append(new String(buffer,0,size,StandardCharsets.ISO_8859_1));
            buffer = new byte[buffer_size];
        }
    }

}

