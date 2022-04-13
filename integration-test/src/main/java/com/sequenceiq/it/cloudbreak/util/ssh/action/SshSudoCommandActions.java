package com.sequenceiq.it.cloudbreak.util.ssh.action;

import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import com.sequenceiq.it.cloudbreak.exception.TestFailException;
import com.sequenceiq.it.cloudbreak.util.ssh.client.SshJClient;

import net.schmizz.sshj.SSHClient;

@Component
public class SshSudoCommandActions extends SshJClient {

    public void checkPermission(Set<String> ipAddresses, String user, String password, String sudoCommand) {
        ipAddresses.stream().forEach(ipAddress -> {
            Pair<Integer, String> result = executeCommand(ipAddress, user, password, "echo " + password + " | sudo -S " + sudoCommand);
            if (result.getKey().intValue() != 0) {
                throw new TestFailException("sudo command failed on '" + ipAddress + "' for user '" + user + "'.");
            }
        });
    }

    public void checkPermissionIsMissing(Set<String> ipAddresses, String user, String password, String sudoCommand) {
        ipAddresses.stream().forEach(ipAddress -> {
            Pair<Integer, String> result = executeCommand(ipAddress, user, password, "echo " + password + " | sudo -S " + sudoCommand);
            if (result.getKey().intValue() == 0) {
                throw new TestFailException("sudo command did not fail on '" + ipAddress + "' for user '" + user + "'.");
            }
        });
    }

    private Pair<Integer, String> executeCommand(String instanceIP, String user, String password, String command) {
        try (SSHClient sshClient = createSshClient(instanceIP, user, password, null)) {
            return execute(sshClient, command);
        } catch (Exception e) {
            throw new TestFailException(" SSH fail on [" + instanceIP + "] while executing command [" + command + "].", e);
        }
    }
}
