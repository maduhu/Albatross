package org.apache.cloudstack.mom.rabbitmq;

import com.cloud.domain.Domain;
import com.cloud.user.Account;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.region.service.AccountService;
import org.apache.cloudstack.framework.events.Event;
import org.apache.cloudstack.region.RegionVO;
import org.apache.log4j.Logger;

import java.lang.reflect.Method;
import java.util.List;

public class AccountSubscriber extends MultiRegionSubscriber {

    private static final Logger s_logger = Logger.getLogger(AccountSubscriber.class);

    public AccountSubscriber(int id)
    {
        super(id);
    }

    @Override
    public void onEvent(Event event)
    {
        super.onEvent(event);

        if (!isExecutable())    return;

        regions = findRemoteRegions();
        process(event);
    }

    protected void process(Event event)
    {
        String entityUUID = this.descMap.get("entityuuid");
        String oldAccountName = this.descMap.get("oldentityname");
        User user = null;
        Account account = this.accountDao.findByUuidIncludingRemoved(entityUUID);
        Domain domain = this.domainDao.findByIdIncludingRemoved(account.getDomainId());
        List<UserVO> users = this.userDao.listByAccount(account.getAccountId());
        if (users.size() > 0)
        {
            user = users.get(0);
        }

        String methodName = event.getEventType().split("-")[1].toLowerCase();
        for (RegionVO region : regions)
        {
            try
            {
                AccountService accountService = new AccountService(region);
                Method method = accountService.getClass().getMethod(methodName, User.class, Account.class, Domain.class, String.class);
                method.invoke(accountService, user, account, domain, oldAccountName);
            }
            catch(NoSuchMethodException mex)
            {
                s_logger.error(region.getName() + ": Not valid method[" + methodName + "]");
            }
            catch(Exception ex)
            {
                s_logger.error(region.getName() + ": Fail to invoke/process method[" + methodName + "]", ex);
            }
        }
    }
}
