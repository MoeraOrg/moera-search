package org.moera.search.rest;

import java.time.Instant;
import java.util.Objects;
import jakarta.inject.Inject;

import org.moera.lib.node.types.ContactInfo;
import org.moera.lib.node.types.Scope;
import org.moera.lib.node.types.SubscriberDescription;
import org.moera.lib.node.types.SubscriberInfo;
import org.moera.lib.node.types.SubscriptionType;
import org.moera.lib.util.LogUtil;
import org.moera.search.api.model.ObjectNotFoundFailure;
import org.moera.search.auth.AuthenticationException;
import org.moera.search.auth.RequestContext;
import org.moera.search.global.ApiController;
import org.moera.search.global.NoCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController
@RequestMapping("/moera/api/people/subscribers")
@NoCache
public class SubscriberController {

    private static final Logger log = LoggerFactory.getLogger(SubscriberController.class);

    // Type 5 UUID ns:URL https://search.moera.org/subscription/profile
    private static final String PROFILE_SUBSCRIBER_ID = "3ce2f176-4988-5482-9eb7-f3ce2f75bd50";

    @Inject
    private RequestContext requestContext;

    @PostMapping
    public SubscriberInfo post(@RequestBody SubscriberDescription subscriberDescription) {
        log.info(
            "POST /people/subscribers (type = {})",
            LogUtil.format(SubscriptionType.toValue(subscriberDescription.getType()))
        );

        if (subscriberDescription.getType() != SubscriptionType.PROFILE) {
            throw new ObjectNotFoundFailure("not-supported");
        }

        String ownerName = requestContext.getClientName(Scope.SUBSCRIBE);
        if (ObjectUtils.isEmpty(ownerName)) {
            throw new AuthenticationException();
        }

        var subscriberInfo = new SubscriberInfo();
        subscriberInfo.setId(PROFILE_SUBSCRIBER_ID);
        subscriberInfo.setType(SubscriptionType.PROFILE);
        subscriberInfo.setNodeName(ownerName);
        subscriberInfo.setCreatedAt(Instant.now().getEpochSecond());

        return subscriberInfo;
    }

    @DeleteMapping("/{id}")
    public ContactInfo delete(@PathVariable String id) {
        log.info("DELETE /people/subscribers/{id} (id = {})", LogUtil.format(id));

        if (!Objects.equals(id, PROFILE_SUBSCRIBER_ID)) {
            throw new ObjectNotFoundFailure("subscriber.not-found");
        }

        var contactInfo = new ContactInfo();
        contactInfo.setNodeName(requestContext.getClientName(Scope.SUBSCRIBE));

        return contactInfo;
    }

}
