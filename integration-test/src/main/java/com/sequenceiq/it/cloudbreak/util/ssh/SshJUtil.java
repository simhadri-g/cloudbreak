package com.sequenceiq.it.cloudbreak.util.ssh;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.instancegroup.InstanceGroupV4Response;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.instance.InstanceGroupResponse;
import com.sequenceiq.it.cloudbreak.dto.AbstractSdxTestDto;
import com.sequenceiq.it.cloudbreak.dto.CloudbreakTestDto;
import com.sequenceiq.it.cloudbreak.util.ssh.action.SshJClientActions;

@Component
public class SshJUtil {
    @Inject
    private SshJClientActions sshJClientActions;

    private SshJUtil() {
    }

    public <T extends AbstractSdxTestDto> T checkFilesOnHostByNameAndPath(T testDto, List<InstanceGroupV4Response> instanceGroups,
            List<String> hostGroupNames, String filePath, String fileName, long requiredNumberOfFiles, String user, String password) {
        return sshJClientActions.checkFilesByNameAndPath(testDto, instanceGroups, hostGroupNames, filePath, fileName, requiredNumberOfFiles, user,
                password);
    }

    public <T extends CloudbreakTestDto> T checkSudoPermissionOnHost(T testDto, List<InstanceGroupResponse> instanceGroups, List<String> hostGroupNames,
            String user, String password, String sudoCommand) {
        return sshJClientActions.checkSudoPermissionOnHost(testDto, instanceGroups, hostGroupNames, user, password, sudoCommand);
    }

    public Map<String, Pair<Integer, String>> checkCloudbreakUserOnFreeIpaHost(List<InstanceGroupResponse> instanceGroups, List<String> hostGroupNames,
            String sudoCommand) {
        return sshJClientActions.executeSshCommandOnFreeIpa(instanceGroups, hostGroupNames, sudoCommand, false);
    }
}
