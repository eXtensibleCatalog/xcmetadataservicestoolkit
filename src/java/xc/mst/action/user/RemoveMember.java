/**
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  */

package xc.mst.action.user;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.log4j.Logger;
import xc.mst.bo.user.User;
import xc.mst.constants.Constants;
import xc.mst.manager.user.DefaultGroupService;
import xc.mst.manager.user.DefaultUserService;
import xc.mst.manager.user.GroupService;
import xc.mst.manager.user.UserService;

/**
 * Removes the association between a user and a group
 *
 * @author Tejaswi Haramurali
 */
public class RemoveMember extends ActionSupport
{
    /** The ID of the user who has to be removed from the group */
    private int userId;

    /** The ID of the group form which th euser should be removed */
    private int groupId;

     /** A reference to the logger for this class */
    static Logger log = Logger.getLogger(Constants.LOGGER_GENERAL);

     /**
     * Overrides default implementation to remove a user-group association.
      * 
     * @return {@link #SUCCESS}
     */
    @Override
    public String execute()
    {
        try
        {
            UserService userService = new DefaultUserService();
            GroupService groupService = new DefaultGroupService();
            User user = userService.getUserById(userId);
            user.removeGroup(groupService.getGroupById(groupId));
            userService.updateUser(user);
            setGroupId(groupId);
            return SUCCESS;
        }
        catch(Exception e)
        {
            log.debug("There was a problem removing the member from the group",e);
            this.addFieldError("removeMemberError", "There was a problem removing the member from the group");
            return INPUT;
        }
    }

     /**
     * Sets the group ID
     *
     * @param groupId group ID
     */
    public void setGroupId(int groupId)
    {
        this.groupId = groupId;
    }

    /**
     * Returns the ID of the group
     *
     * @return group ID
     */
    public int getGroupId()
    {
        return this.groupId;
    }

    /**
     * Sets the user ID
     *
     * @param userId user ID
     */
    public void setUserId(int userId)
    {
        this.userId = userId;
    }

    /**
     * Returns the ID of the user
     *
     * @return user ID
     */
    public int getUserId()
    {
        return this.userId;
    }
}
