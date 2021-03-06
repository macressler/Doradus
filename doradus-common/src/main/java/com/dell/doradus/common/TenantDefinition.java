/*
 * Copyright (C) 2014 Dell, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dell.doradus.common;

import java.util.HashMap;
import java.util.Map;

import com.dell.doradus.common.UNode;
import com.dell.doradus.common.Utils;

/**
 * Holds the definition of a Doradus tenant, which consists of the following: 
 * <ul>
 * <li>Name: The unique tenant name within a Doradus database instance.
 * <li>Users: A set of {@link UserDefinition}s, which also define the (non-super) users
 *     allowed to access the applications in the tenant.
 * <li>Options: These are system-defined key/value pairs that control various aspects of
 *     the tenant such as replication factor.
 * <li>Properties: There are application-defined key/value pairs that are not meaningful
 *     to Doradus but help identify or track the tenant.
 * </ul>
 */
public class TenantDefinition implements JSONable{
    // Members:
    private String m_name;
    private final Map<String, UserDefinition> m_users = new HashMap<>();
    private final Map<String, Object> m_options = new HashMap<>();      // system-defined
    private final Map<String, String> m_properties = new HashMap<>();   // application-defined

    /**
     * Create a new TenantDefinition with all null/empty values.
     */
    public TenantDefinition() {}
    
    //----- Getters

    /**
     * Get the tenant's name. The tenant name is the same as the keyspace created for the
     * tenant.
     * 
     * @return  Tenant name, if assigned.
     */
    public String getName() {
        return m_name;
    }
    
    /**
     * Search for a {@link UserDefinition} belonging to this tenant definition with the
     * give user ID.
     * 
     * @param userid    User ID (login name) of user to search for.
     * @return          {@link UserDefinition} of user if defined for this tenant,
     *                  otherwise null.
     */
    public UserDefinition getUser(String userid) {
        return m_users.get(userid);
    }

    /**
     * Get the map of {@link UserDefinition}s keyed by user ID defined for this tenant.
     * 
     * @return  Map of user IDs to {@link UserDefinition}s.
     */
    public Map<String, UserDefinition> getUsers() {
        return m_users;
    }
    
    /**
     * The number of {@link UserDefinition}s currently defined for this tenant.
     * 
     * @return  Count of {@link UserDefinition}s currently defined.
     */
    public int userCount() {
        return m_users.size();
    }

    /**
     * Get this tenant definition's options, if any. Options are system-defined such as
     * settings for the tenant's keyspace. 
     * 
     * @return  Tenant definition options as key/value pairs.
     */
    public Map<String, Object> getOptions() {
        return m_options;
    }
    
    /**
     * Get the value of the option with the given name, if defined. Compared to
     * properties, which are application-defined, options are system-defined.
     * 
     * @param optName   Name of option to fetch.
     * @return          Option value or null if not defined.
     */
    public Object getOption(String optName) {
        return m_options.get(optName);
    }
    
    /**
     * Get the value of the option with the given name, if defined, as a String. If the
     * option is defined, toString() is called to create the String.
     * 
     * @param optName   Name of option to fetch.
     * @return          Option value as a String or null if not defined.
     */
    public String getOptionString(String optName) {
        Object optValue = m_options.get(optName);
        if (optValue == null) {
            return null;
        }
        return optValue.toString();
    }
    
    /**
     * Get the value of the option with the given name as a Map. If the option is not
     * defined, null is returned. If the option value is not a Map, an
     * IllegalArgumentException is thrown.
     * 
     * @param optName   Name of option to fetch.
     * @return          Option value as a String-to-Object map or null if not defined.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOptionMap(String optName) {
        Object optValue = m_options.get(optName);
        if (optValue == null) {
            return null;
        }
        if (!(optValue instanceof Map)) {
            throw new IllegalArgumentException("Tenant option '" + optName + "' should be a map: " + optValue);
        }
        return (Map<String, Object>)optValue;
    }
    
    /**
     * Get the properties defined for this tenant definition as a key value map. Compared
     * to options, which are system-defined, options are application-defined.
     *  
     * @return  Defined properties as a key/value map.
     */
    public Map<String, String> getProperties() {
        return m_properties;
    }
    
    /**
     * Get the value of the property with the given name if defined. Compared to options,
     * which are system-defined, options are application-defined.
     * 
     * @param  propName Name of a property.
     * @return          Property value or null if the property is not defined.
     */
    public String getProperty(String propName) {
        return m_properties.get(propName);
    }

    /**
     * Return this Tenant definition as a UNode tree, which can be serialized into text
     * via {@link UNode#toJSON()} or {@link UNode#toXML()}.
     * 
     * @return  This Tenant definition serialized as a UNode tree.
     */
    public UNode toDoc() {
        UNode tenantNode = UNode.createMapNode(m_name, "tenant");
        if (m_options.size() > 0) {
            UNode optsNode = tenantNode.addMapNode("options");
            for (Map.Entry<String, Object> mapEntry : m_options.entrySet()) {
                optsNode.addChildNode(optionValueToUNode(mapEntry.getKey(), mapEntry.getValue()));
            }
        }
        if (m_properties.size() > 0) {
            UNode propsNode = tenantNode.addMapNode("properties");
            for (Map.Entry<String, String> mapEntry : m_properties.entrySet()) {
                propsNode.addValueNode(mapEntry.getKey(), mapEntry.getValue(), "property");
            }
        }
        if (m_users.size() > 0) {
            UNode usersNode = tenantNode.addMapNode("users");
            for (UserDefinition userDef : m_users.values()) {
                usersNode.addChildNode(userDef.toDoc());
            }
        }
        return tenantNode;
    }   // toDoc
    
    // Return a UNode that represents the given option's serialized value.
    @SuppressWarnings("unchecked")
    private UNode optionValueToUNode(String optName, Object optValue) {
        if (!(optValue instanceof Map)) {
            if (optValue == null) {
                optValue = "";
            }
            return UNode.createValueNode(optName, optValue.toString(), "option");
        }
        UNode optNode = UNode.createMapNode(optName, "option");
        Map<String, Object> suboptMap = (Map<String,Object>)optValue;
        for (String suboptName : suboptMap.keySet()) {
            optNode.addChildNode(optionValueToUNode(suboptName, suboptMap.get(suboptName)));
        }
        return optNode;
    }

    // For debugging
    @Override
    public String toString() {
        return "Tenant: " + m_name;
    }

    //----- Setters
    
    /**
     * Set the tenant name of this tenant definition. The existing name, if any, is
     * overwritten.
     * 
     * @param tenantName    New name for tenant.
     */
    public void setName(String tenantName) {
        m_name = tenantName;
    }   // setName
    
    /**
     * Add the given {@link UserDefinition} to this tenant definition's list of defined
     * users. If a user with the given name already exists, an InvalidArgumentException is
     * thrown.
     * 
     * @param userDef   New {@link UserDefinition} to add to this tenant definition.
     */
    public void addUser(UserDefinition userDef) {
        String userID = userDef.getID();
        Utils.require(!Utils.isEmpty(userID), "User ID must be set");
        Utils.require(!m_users.containsKey(userID), "Duplicate user ID: " + userID);
        m_users.put(userID, userDef);
    }
    
    /**
     * Set the given option. If the option was previously defined, the value is
     * overwritten. If the given value is null, the existing option is removed.
     * 
     * @param optName   Option name.
     * @param optValue  Option value, which may be a String or Map&lt;String,Object&gt;.
     */
    public void setOption(String optName, Object optValue) {
        if (optValue == null) {
            m_options.remove(optName);
        } else {
            m_options.put(optName, optValue);
        }
    }
    
    /**
     * Set the given property. If the property was previously defined, the value is
     * overwritten.
     * 
     * @param name  Property name.
     * @param value New property value.
     */
    public void setProperty(String name, String value) {
        m_properties.put(name, value);
    }

    /**
     * Parse the tenant definition rooted at given UNode tree and update this object to
     * match. The root node is the "tenant" object, so its name is the tenant name and its
     * child nodes are tenant definitions such as "users" and "options". An exception is
     * thrown if the definition contains an error.
     *  
     * @param tenantNode    Root of a UNode tree that defines a tenant.
     */
    public void parse(UNode tenantNode) {
        assert tenantNode != null;
        m_name = tenantNode.getName();
        
        for (String childName : tenantNode.getMemberNames()) {
            UNode childNode = tenantNode.getMember(childName);
            switch (childNode.getName()) {
            case "options":
                for (UNode optNode : childNode.getMemberList()) {
                    m_options.put(optNode.getName(), parseOption(optNode));
                }
                break;
            case "users":
                for (UNode userNode : childNode.getMemberList()) {
                    UserDefinition userDef = new UserDefinition();
                    userDef.parse(userNode);
                    m_users.put(userDef.getID(), userDef);
                }
                break;
            case "properties":
                for (UNode propNode : childNode.getMemberList()) {
                    Utils.require(propNode.isValue(), "'property' must be a value: " + propNode);
                    m_properties.put(propNode.getName(), propNode.getValue());
                }
                break;
            default:
                Utils.require(false, "Unknown tenant property: " + childNode);
            }
        }            
    }   // parse

    // Parse an option UNode, which may be a value or a map.
	private Object parseOption(UNode optionNode) {
        if (optionNode.isValue()) {
            return optionNode.getValue();
        }
        Map<String, Object> optValueMap = new HashMap<>();
        for (UNode suboptNode : optionNode.getMemberList()) {
            optValueMap.put(suboptNode.getName(), parseOption(suboptNode));
        }
        return optValueMap;
    }

    @Override
    public String toJSON() {
        return toDoc().toJSON();
    }

}   // class TenantDefinition
