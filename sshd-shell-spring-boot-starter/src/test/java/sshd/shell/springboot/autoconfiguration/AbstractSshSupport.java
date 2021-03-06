/*
 * Copyright 2017 anand.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sshd.shell.springboot.autoconfiguration;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import static org.awaitility.Awaitility.await;
import org.awaitility.Duration;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 *
 * @author anand
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ConfigTest.class)
abstract class AbstractSshSupport {
    
    @Autowired
    protected SshdShellProperties props;
    
    protected void sshCall(String username, String password, SshExecutor executor) throws JSchException, IOException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, props.getShell().getHost(), props.getShell().getPort());
        session.setPassword(password);
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect();
        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        PipedInputStream pis = new PipedInputStream();
        PipedOutputStream pos = new PipedOutputStream();
        channel.setInputStream(new PipedInputStream(pos));
        channel.setOutputStream(new PipedOutputStream(pis));
        channel.connect();
        try {
            executor.execute(pis, pos);
        } finally {
            pis.close();
            pos.close();
            channel.disconnect();
            session.disconnect();
        }
    }
    
    protected void sshCall(SshExecutor executor) throws JSchException, IOException {
        sshCall(props.getShell().getUsername(), props.getShell().getPassword(), executor);
    }
    
    protected void verifyResponse(InputStream pis, String response) {
        StringBuilder sb = new StringBuilder();
        try {
            await().atMost(Duration.TWO_SECONDS).until(() -> {
                while (true) {
                    sb.append((char) pis.read());
                    String s = sb.toString();
                    if (s.contains(response)) {
                        break;
                    }
                }
                return true;
            });
        } finally {
            System.out.println(sb.toString());
        }
    }

    protected void write(OutputStream os, String... input) throws IOException {
        for (String s : input) {
            os.write((s + '\r').getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }
    
    @FunctionalInterface
    protected static interface SshExecutor {
        
        void execute(InputStream is, OutputStream os) throws IOException;
    }
}
