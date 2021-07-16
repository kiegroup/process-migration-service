package org.kie.processmigration.security;

import io.quarkus.arc.Unremovable;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.wildfly.security.auth.server.RealmMapper;
import org.wildfly.security.evidence.Evidence;

import javax.enterprise.context.ApplicationScoped;
import java.security.Principal;

@ApplicationScoped
@Unremovable
public class PropertyBasedRealmMapper implements RealmMapper {

    @ConfigProperty(name = "pim.auth-method", defaultValue = "file")
    String authType;

    @Override
    public String getRealmMapping(Principal principal, Evidence evidence) {
        return "pim_" + authType;
    }


}
