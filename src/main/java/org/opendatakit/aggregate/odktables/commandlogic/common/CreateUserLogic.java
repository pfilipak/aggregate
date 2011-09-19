package org.opendatakit.aggregate.odktables.commandlogic.common;

import org.opendatakit.aggregate.odktables.client.entity.User;
import org.opendatakit.aggregate.odktables.client.exception.AggregateInternalErrorException;
import org.opendatakit.aggregate.odktables.client.exception.UserAlreadyExistsException;
import org.opendatakit.aggregate.odktables.command.common.CreateUser;
import org.opendatakit.aggregate.odktables.commandlogic.CommandLogic;
import org.opendatakit.aggregate.odktables.commandresult.CommandResult.FailureReason;
import org.opendatakit.aggregate.odktables.commandresult.common.CreateUserResult;
import org.opendatakit.aggregate.odktables.entity.InternalUser;
import org.opendatakit.aggregate.odktables.relation.Permissions;
import org.opendatakit.aggregate.odktables.relation.Users;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.web.CallingContext;

/**
 * CreateUserLogic encapsulates the logic necessary to validate and execute a
 * CreateUser command.
 * 
 * @author the.dylan.price@gmail.com
 * 
 */
public class CreateUserLogic extends CommandLogic<CreateUser>
{

    private final CreateUser createUser;

    public CreateUserLogic(CreateUser createUser)
    {
        this.createUser = createUser;
    }

    @Override
    public CreateUserResult execute(CallingContext cc) throws AggregateInternalErrorException
    {
        User user;
        try
        {
            Users users = Users.getInstance(cc);
            
            String userID = createUser.getUserID();
            String userName = createUser.getUserName();
            String requestingUserID = createUser.getRequestingUserID();
            String aggregateUsersIdentifier = users.getAggregateIdentifier();
    
            InternalUser requestUser = users.query("CreateUserLogic.execute")
                    .equal(Users.USER_ID, requestingUserID).get();
    
            if (!requestUser.hasPerm(aggregateUsersIdentifier, Permissions.WRITE))
            {
                return CreateUserResult.failure(userID,
                        FailureReason.PERMISSION_DENIED);
            }
    
            try
            {
                user = createUser(users, userID, userName);
            }
            catch (UserAlreadyExistsException e)
            {
                return CreateUserResult.failure(userID,
                        FailureReason.USER_ALREADY_EXISTS);
            }
        }
        catch (ODKDatastoreException e)
        {
            throw new AggregateInternalErrorException(e.getMessage());
        }

        return CreateUserResult.success(user);
    }
    
    public static User createUser(Users users, String userID, String userName) throws UserAlreadyExistsException, ODKDatastoreException
    {
        if (users.query("CreateUserLogic.createUser").equal(Users.USER_ID, userID).exists())
        {
            throw new UserAlreadyExistsException(null);
        }
        InternalUser newUser = new InternalUser(userID,
                userName, users.getCC());
        
        newUser.save();

        User user = new User(newUser.getID(), newUser.getAggregateIdentifier(),
                newUser.getName());
        
        return user;
    }
}
