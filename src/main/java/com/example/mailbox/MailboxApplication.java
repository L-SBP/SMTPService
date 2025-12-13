package com.example.mailbox;

import com.example.mailbox.server.Pop3Server;
import com.example.mailbox.server.SmtpServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MailboxApplication implements CommandLineRunner {

    @Autowired
    private SmtpServer smtpServer;

    @Autowired
    private Pop3Server pop3Server;

    public static void main(String[] args) {
        SpringApplication.run(MailboxApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 启动 Socket 服务
        // 注意：这两个方法内部是开启新线程的，不会阻塞主线程
        smtpServer.start();
        pop3Server.start();
    }
}