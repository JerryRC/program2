package com.java;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class Client {

    static BufferedReader keyboard = new BufferedReader(new InputStreamReader(
            System.in));

    static PrintWriter screen = new PrintWriter(System.out, true);

    public static void main(String[] args) {
        try {
            HttpClient myClient = new HttpClient();

            if (args.length != 1) {
                System.err.println("Usage: Client <server>");
                System.exit(0);
            }

            myClient.connect(args[0]);
            System.out.println(System.getProperty("user.dir"));

            screen.println(args[0] + " is listening to your request:");
            String request = keyboard.readLine();

            if (request.startsWith("GET ")) {
                myClient.processGetRequest(request);
            } else if (request.startsWith("PUT ")) {
                myClient.processPutRequest(request);
            } else {
                screen.println("Bad request! \n");
                myClient.close();
                return;
            }

            screen.println("Header: ");
            screen.println(myClient.getHeader());

            if (request.startsWith("GET")) {
                screen.print("Enter the name of the file to save: ");
                screen.flush();
                String filename = keyboard.readLine();
                FileOutputStream outfile = new FileOutputStream(filename);

                String response = myClient.getResponse();
                outfile.write(response.getBytes(StandardCharsets.ISO_8859_1));
                outfile.flush();
                outfile.close();

//                screen.print("Response: \n" + response);
//                screen.flush();
            } else if (request.startsWith("PUT")) {
                screen.print("Response: \n" + myClient.getResponse());
                screen.flush();
            }

            myClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

