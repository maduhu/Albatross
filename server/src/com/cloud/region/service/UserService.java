package com.cloud.region.service;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONObject;
import com.cloud.domain.Domain;
import com.cloud.rmap.RmapVO;
import com.cloud.rmap.dao.RmapDao;
import com.cloud.user.Account;
import com.cloud.user.User;
import com.cloud.region.api_interface.BaseInterface;
import com.cloud.region.api_interface.UserInterface;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.region.RegionVO;
import org.apache.log4j.Logger;

public class UserService extends BaseService {

    private static final Logger s_logger = Logger.getLogger(UserService.class);
    private UserInterface apiInterface;

    private RegionVO region;
    private RmapDao rmapDao;

    public UserService(RegionVO region)
    {
        super(region.getName(), region.getEndPoint(), region.getUserName(), region.getPassword());
        this.apiInterface = null;

        this.region = region;
        this.rmapDao = ComponentContext.getComponent(RmapDao.class);
    }

    public UserService(String hostName, String endPoint, String userName, String password)
    {
        super(hostName, endPoint, userName, password);
        this.apiInterface = null;
    }

    private boolean isEqual(JSONObject userJson, String userName, String email, String firstName, String lastName, String password, String timezone, String apiKey, String secretKey)
    {
        String jsonUserName = getAttrValue(userJson, "username");
        String jsonEmail = getAttrValue(userJson, "email");
        String jsonFirstName = getAttrValue(userJson, "firstname");
        String jsonLastName = getAttrValue(userJson, "lastname");
        String jsonApiKey = getAttrValue(userJson, "apikey");
        String jsonSecretKey = getAttrValue(userJson, "secretkey");
        String jsonTimezone = getAttrValue(userJson, "timezone");
        String jsonPassword = getAttrValue(userJson, "password");

        if(!jsonUserName.equals(userName))    return false;
        if(!jsonEmail.equals(email))    return false;
        if(!jsonFirstName.equals(firstName))    return false;
        if(!jsonLastName.equals(lastName))    return false;

        if(jsonApiKey != null || apiKey != null)
        {
            if(jsonApiKey == null && apiKey != null)  return false;
            if(jsonApiKey != null && apiKey == null)  return false;
            if(!jsonApiKey.equals(apiKey))    return false;
        }

        if(jsonSecretKey != null || secretKey != null)
        {
            if(jsonSecretKey == null && secretKey != null)    return false;
            if(jsonSecretKey != null && secretKey == null)    return false;
            if(!jsonSecretKey.equals(secretKey))    return false;
        }

        if(jsonTimezone != null || timezone != null)
        {
            if(jsonTimezone == null && timezone != null)  return false;
            if(jsonTimezone != null && timezone == null)  return false;
            if(!jsonTimezone.equals(timezone))    return false;
        }

        //if(!jsonPassword.equals(password))    return false;

        return true;
    }

    @Override
    protected BaseInterface getInterface()
    {
        return this.apiInterface;
    }

    private JSONObject find(String[] attrNames, String[] attrValues)
    {
        try
        {
            JSONArray userArray = this.apiInterface.listUsers(null, null);
            JSONObject userObj = findJSONObject(userArray, attrNames, attrValues);
            return userObj;
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    private JSONObject find(String uuid)
    {
        try
        {
            JSONObject userObj = this.apiInterface.findUser(uuid);
            return userObj;
        }
        catch(Exception ex)
        {
            return null;
        }
    }

    private void saveRmap(User user, JSONObject resJson)
    {
        try
        {
            RmapVO rmapVO = new RmapVO(user.getUuid(), region.getId(), BaseService.getAttrValue(resJson.getJSONObject("user"), "id"));
            rmapDao.create(rmapVO);
        }
        catch(Exception ex)
        {

        }
    }

    public JSONArray list(String domainId, String accountName)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            JSONArray userArray = this.apiInterface.listUsers(domainId, accountName);
            s_logger.debug("Successfully found user list");
            return userArray;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find users", ex);
            return new JSONArray();
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject findById(String id)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            String[] attrNames = {"id"};
            String[] attrValues = {id};
            JSONObject userJson = find(attrNames, attrValues);
            s_logger.debug("Successfully found user by id[" + id + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find user by id[" + id + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject findByName(String userName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);
            String[] attrNames = {"username", "path"};
            String[] attrValues = {userName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            s_logger.debug("Successfully found user by name[" + userName + ", " + domainPath + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to find user by name[" + userName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean create(User user, Account account, Domain domain, String oldUserName)
    {
        JSONObject resJson = create(user.getUsername(), account.getAccountName(), domain.getPath(), user.getPassword(), user.getEmail(), user.getFirstname(), user.getLastname(), user.getTimezone());
        if (resJson != null)
        {
            saveRmap(user, resJson);
            return true;
        }

        return false;
    }

    public JSONObject create(String userName, String accountName, String domainPath, String password, String email, String firstName, String lastName, String timezone)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the user already exists
            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson != null)
            {
                s_logger.debug("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] already exists in host[" + this.hostName + "]");
                return userJson;
            }

            // find domain id
            DomainService domainService = new DomainService(this.hostName, this.endPoint, this.userName, this.password);
            JSONObject domainObj = domainService.findByPath(domainPath);
            if (domainObj == null)
            {
                s_logger.error("cannot find domain[" + domainPath + "] in host[" + this.hostName + "]");
                return null;
            }
            String domainId = (String)domainObj.get("id");

            userJson = this.apiInterface.createUser(userName, password, email, firstName, lastName, accountName, domainId, timezone);
            s_logger.debug("Successfully created user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to create user with name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean delete(User user, Account account, Domain domain, String oldUserName)
    {
        RmapVO rmap = rmapDao.findBySource(user.getUuid(), region.getId());

        JSONObject resJson = null;
        if (rmap == null)
        {
            resJson = delete(user.getUsername(), account.getAccountName(), domain.getPath());
            if (resJson != null)
            {
                saveRmap(user, resJson);
            }
        }
        else
        {
            deleteByUuid(rmap.getUuid());
        }

        return (resJson != null);
    }

    protected JSONObject deleteByUuid(String uuid)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the user already exists
            JSONObject userJson = find(uuid);
            if (userJson == null)
            {
                s_logger.error("user[" + uuid + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            this.apiInterface.deleteUser(uuid);
            s_logger.debug("Successfully deleted user[" + uuid + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete user by name[" + uuid + "in host[" + this.hostName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject delete(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            // check if the user already exists
            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.error("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.deleteUser(id);
            s_logger.debug("Successfully deleted user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to delete user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean enable(User user, Account account, Domain domain, String oldUserName)
    {
        RmapVO rmap = rmapDao.findBySource(user.getUuid(), region.getId());

        JSONObject resJson = null;
        if (rmap == null)
        {
            resJson = enable(user.getUsername(), account.getAccountName(), domain.getPath());
            if (resJson != null)
            {
                saveRmap(user, resJson);
            }
        }
        else
        {
            enableByUuid(rmap.getUuid());
        }

        return (resJson != null);
    }

    protected JSONObject enableByUuid(String uuid)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            JSONObject userJson = find(uuid);
            if (userJson == null)
            {
                s_logger.error("user[" + uuid + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_ENABLED))
            {
                s_logger.debug("user[" + uuid + "] in host[" + this.hostName + "] is already enabled in host[" + this.hostName + "]");
                return userJson;
            }

            this.apiInterface.enableUser(uuid);
            s_logger.debug("Successfully enabled user[" + uuid + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to enable user by name[" + uuid + "] in host[" + this.hostName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject enable(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.error("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_ENABLED))
            {
                s_logger.debug("user[" + userName + "] in account[" + accountName + "] in domain[" + domainPath + "] is already enabled in host[" + this.hostName + "]");
                return userJson;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.enableUser(id);
            s_logger.debug("Successfully enabled user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to enable user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean disable(User user, Account account, Domain domain, String oldUserName)
    {
        RmapVO rmap = rmapDao.findBySource(user.getUuid(), region.getId());

        JSONObject resJson = null;
        if (rmap == null)
        {
            resJson = disable(user.getUsername(), account.getAccountName(), domain.getPath());
            if (resJson != null)
            {
                saveRmap(user, resJson);
            }
        }
        else
        {
            disableByUuid(rmap.getUuid());
        }

        return (resJson != null);
    }

    protected JSONObject disableByUuid(String uuid)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            JSONObject userJson = find(uuid);
            if (userJson == null)
            {
                s_logger.error("user[" + uuid + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_DISABLED))
            {
                s_logger.debug("user[" + uuid + "] in host[" + this.hostName + "] is already disabled in host[" + this.hostName + "]");
                return userJson;
            }

            JSONObject retJson = this.apiInterface.disableUser(uuid);
            queryAsyncJob(retJson);
            s_logger.debug("Successfully disabled user[" + uuid + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable user by name[" + uuid + "] in host[" + this.hostName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject disable(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.error("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "]  does not exists in host[" + this.hostName + "]");
                return null;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_DISABLED))
            {
                s_logger.debug("user[" + userName + "] in account[" + accountName + "] in domain[" + domainPath + "] is already disabled in host[" + this.hostName + "]");
                return userJson;
            }

            String id = getAttrValue(userJson, "id");
            JSONObject retJson = this.apiInterface.disableUser(id);
            queryAsyncJob(retJson);
            s_logger.debug("Successfully disabled user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean lock(User user, Account account, Domain domain, String oldUserName)
    {
        RmapVO rmap = rmapDao.findBySource(user.getUuid(), region.getId());

        JSONObject resJson = null;
        if (rmap == null)
        {
            resJson = lock(user.getUsername(), account.getAccountName(), domain.getPath());
            if (resJson != null)
            {
                saveRmap(user, resJson);
            }
        }
        else
        {
            lockByUuid(rmap.getUuid());
        }

        return (resJson != null);
    }

    protected JSONObject lockByUuid(String uuid)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            JSONObject userJson = find(uuid);
            if (userJson == null)
            {
                s_logger.error("user[" + uuid + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_LOCKED))
            {
                s_logger.debug("user[" + uuid + "] in host[" + this.hostName + "] is already locked in host[" + this.hostName + "]");
                return userJson;
            }

            this.apiInterface.lockUser(uuid);
            s_logger.debug("Successfully disabled user[" + uuid + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable user by name[" + uuid + "] in host[" + this.hostName + "] ", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject lock(String userName, String accountName, String domainPath)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.error("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "]  does not exists in host[" + this.hostName + "]");
                return null;
            }

            String state = getAttrValue(userJson, "state");
            if (state.equals(Account.ACCOUNT_STATE_LOCKED))
            {
                s_logger.debug("user[" + userName + "] in account[" + accountName + "] in domain[" + domainPath + "] is already locked in host[" + this.hostName + "]");
                return userJson;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.lockUser(id);
            s_logger.debug("Successfully disabled user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to disable user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public boolean update(User user, Account account, Domain domain, String oldUserName)
    {
        RmapVO rmap = rmapDao.findBySource(user.getUuid(), region.getId());

        JSONObject resJson = null;
        if (rmap == null)
        {
            resJson = update(oldUserName, user.getUsername(), account.getAccountName(), domain.getPath(), user.getEmail(), user.getFirstname(), user.getLastname(), user.getPassword(), user.getTimezone(), user.getApiKey(), user.getSecretKey());
            if (resJson != null)
            {
                saveRmap(user, resJson);
            }
        }
        else
        {
            updateByUuid(rmap.getUuid(), user.getUsername(), user.getEmail(), user.getFirstname(), user.getLastname(), user.getPassword(), user.getTimezone(), user.getApiKey(), user.getSecretKey());
        }

        return (resJson != null);
    }

    protected JSONObject updateByUuid(String uuid, String newName, String email, String firstName, String lastName, String password, String timezone, String userAPIKey, String userSecretKey)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            JSONObject userJson = find(uuid);
            if (userJson == null)
            {
                s_logger.error("user[" + uuid + "] does not exists in host[" + this.hostName + "]");
                return null;
            }

            if(isEqual(userJson, newName, email, firstName, lastName, password, timezone, userAPIKey, userSecretKey))
            {
                s_logger.debug("account[" + uuid + "] in host[" + this.hostName + "] has same attrs");
                return userJson;
            }

            this.apiInterface.updateUser(uuid, email, firstName, lastName, password, timezone, userAPIKey, newName, userSecretKey);
            s_logger.debug("Successfully updated user[" + uuid + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to update user by name[" + uuid + "] in host[" + this.hostName + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }

    public JSONObject update(String userName, String newName, String accountName, String domainPath, String email, String firstName, String lastName, String password, String timezone, String userAPIKey, String userSecretKey)
    {
        this.apiInterface = new UserInterface(this.url);
        try
        {
            this.apiInterface.login(this.userName, this.password);

            String[] attrNames = {"username", "account", "path"};
            String[] attrValues = {userName, accountName, domainPath};
            JSONObject userJson = find(attrNames, attrValues);
            if (userJson == null)
            {
                s_logger.error("user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "]  does not exists in host[" + this.hostName + "]");
                return null;
            }

            if(isEqual(userJson, newName, email, firstName, lastName, password, timezone, userAPIKey, userSecretKey))
            {
                s_logger.debug("account[" + newName + "] has same attrs in host[" + this.hostName + "]");
                return userJson;
            }

            String id = getAttrValue(userJson, "id");
            this.apiInterface.updateUser(id, email, firstName, lastName, password, timezone, userAPIKey, newName, userSecretKey);
            s_logger.debug("Successfully updated user[" + userName + "] in account[" + accountName + "], domain[" + domainPath + "] in host[" + this.hostName + "]");
            return userJson;
        }
        catch(Exception ex)
        {
            s_logger.error("Failed to update user by name[" + userName + ", " + accountName + ", " + domainPath + "]", ex);
            return null;
        }
        finally {
            this.apiInterface.logout();
        }
    }
}
