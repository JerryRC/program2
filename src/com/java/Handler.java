package com.java;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    //http 请求
    private final StringBuffer header;
    private final StringBuffer body;
    //http 响应头
    private String response;

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
            processRequests();

//            System.out.println("header: \n" + header.toString()); //输出头部
//            System.out.println("body: \n" + body.toString()); //输出消息体

            StringTokenizer st = new StringTokenizer(header.toString(), " ");
            String command = st.nextToken();    //第一个分段为指令名

            boolean BAD = true;
            //GET请求
            if (command.equals("GET")) {
                String filename = st.nextToken();   //第二个分段就是文件路径
                String protocol = st.nextToken().trim();  //第三个分段是协议

                //第三个分段若不是 HTTP/1.0 则 bad request
                if (protocol.contains("HTTP/1.0\n") || protocol.equals("HTTP/1.0")) {
                    //格式正确
                    BAD = false;
                    String result = getFile(filename, currentPath);
                    //没有这个文件 404
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
                    //返回指定文件 200
                    else {
                        func200(result);
                    }
                }
            }
            //PUT请求
            else if (command.equals("PUT")) {
                String filename = st.nextToken();   //第二个分段就是文件路径
                String protocol = st.nextToken().trim();  //第三个分段是协议

                if (protocol.contains("HTTP/1.0\n") || protocol.equals("HTTP/1.0")) {
                    //格式正确
                    BAD = false;
                    //取文件名
                    int start = Math.max(filename.lastIndexOf("\\"), filename.lastIndexOf("/"));
                    filename = filename.substring(start + 1);
                    //创建文件，将 body 写入
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
            //其他指令 400
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
     * 查询文件存在性
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
     * 处理 200 OK
     *
     * @param result getFile()的返回，文件绝对路径
     * @throws IOException 读写错误
     */
    private void func200(String result) throws IOException {
        boolean html = false;  //是否要检查 html 的图片嵌入

        String type = "application/octet-stream";
        if (result.contains(".htm")) {
            type = "text/html";
            html = true;
        } else if (result.endsWith(".jpg") || result.endsWith(".jpeg")) {
            type = "image/jpeg";
        } else if (result.endsWith(".png")) {
            type = "image/png";
        } else if (result.endsWith(".gif")) {
            type = "image/gif";
        } else if (result.endsWith(".txt")) {
            type = "text/plain";
        }

        //处理 html 的 img 嵌入
        if (html) {
            embeddingIMG(result);
        }

        //传输处理好的文件
        File f = html ? new File(result + "_img") : new File(result);
        FileInputStream fis = new FileInputStream(f);

        //响应头
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

        //响应体，文件转换成 byte[] 输出
        int size;
        while ((size = fis.read(buffer)) != -1) {
            OStream.write(buffer, 0, size);
            OStream.flush();
            //刷新buffer
            buffer = new byte[buffer_size];
        }
        fis.close();
    }

    /**
     * 扫描 html 文件，处理<img>标签
     * 创建临时文件 html_img
     *
     * @param result html文件的路径
     * @throws IOException 文件不存在及读写错误
     */
    private void embeddingIMG(String result) throws IOException {
        //原文件装饰成流
        File f = new File(result);
        FileInputStream fis = new FileInputStream(f);
        BufferedReader fbr = new BufferedReader(new InputStreamReader(fis));

        //新建一个嵌入 img 的临时文件
        File tmp = new File(result + "_img");
        FileOutputStream fos = new FileOutputStream(tmp);

        String line;
        while ((line = fbr.readLine()) != null) {
            //有 img 标签，得到图片地址，更新 line
            if (line.contains("<img")) {
                line = encodingBASE64(line);
            }
            //没有 img 标签直接写入
            fos.write(line.getBytes());
            fos.write(CRLF.getBytes());
            fos.flush();
        }

        fbr.close();
        fos.close();
    }

    /**
     * 若存在 <img> 标签则进行处理
     *
     * @param line 包含 <img> 标签的行
     * @return 返回处理好的行
     * @throws IOException 文件读写错误
     */
    private String encodingBASE64(String line) throws IOException {

        //定位 <img> 标签
        int startIMG = line.indexOf("<img");
        //定位 <img> 的 src
        int start = line.indexOf("src=", startIMG);
        //找到开始的 " 的位置
        start = line.indexOf("\"", start);
        //找到结尾
        int end = line.indexOf(".jpg", start);
        //截取 url ，这里定死了只能处理 .jpg 情况
        String path = line.substring(start + 1, end + 4);
        File img = new File(currentPath + "/" + path);

        //确实图片是否存在
        if (img.isFile()) {
            //以二进制字节流读入图片
            FileInputStream IMGin = new FileInputStream(img);
            byte[] IMGdata = new byte[IMGin.available()];

            System.out.println("img 数据大小: " + IMGin.read(IMGdata));
            IMGin.close();

            //Base64 编码
            Base64.Encoder encoder = Base64.getEncoder();
            String str = encoder.encodeToString(IMGdata).trim().
                    replaceAll("\n", "").
                    replaceAll("\r", "");

            //现在只固定格式处理 base64 jpg 的内嵌
            return line.replace(path, "data:image/jpeg;base64," + str);
        }
        //找不到图片
        return line;
    }

    /**
     * 用于处理 http 报文
     * 为 header 和 body 赋值
     *
     * @throws IOException 各种错误
     */
    private void processRequests() throws IOException {
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
            //向上取整
            int times = (len + buffer_size - 1) / buffer_size;

            int size;
            for (int i = 0; i < times && (size = IStream.read(buffer)) != -1; ++i) {
                //body 为 http 报文内容
                body.append(new String(buffer, 0, size, StandardCharsets.ISO_8859_1));
                //刷新 buffer
                buffer = new byte[buffer_size];
            }
        }
    }

}

