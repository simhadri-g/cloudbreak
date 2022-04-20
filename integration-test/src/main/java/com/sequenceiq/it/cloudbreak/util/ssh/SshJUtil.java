package com.sequenceiq.it.cloudbreak.util.ssh;

import java.util.List;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.instancegroup.InstanceGroupV4Response;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.instance.InstanceGroupResponse;
import com.sequenceiq.it.cloudbreak.dto.AbstractSdxTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaTestDto;
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

    public FreeIpaTestDto checkSudoPermissionOnHost(FreeIpaTestDto testDto, List<InstanceGroupResponse> instanceGroups, List<String> hostGroupNames,
            String user, String password) {
        return sshJClientActions.checkSudoPermissionOnHost(testDto, instanceGroups, hostGroupNames, user, password);
    }
}
