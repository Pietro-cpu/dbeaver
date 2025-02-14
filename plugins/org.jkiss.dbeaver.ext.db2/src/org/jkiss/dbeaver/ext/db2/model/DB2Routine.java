/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2013-2017 Denis Forveille (titou10.titou10@gmail.com)
 * Copyright (C) 2010-2023 DBeaver Corp and others
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
package org.jkiss.dbeaver.ext.db2.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.db2.DB2Constants;
import org.jkiss.dbeaver.ext.db2.DB2Messages;
import org.jkiss.dbeaver.ext.db2.DB2Utils;
import org.jkiss.dbeaver.ext.db2.editors.DB2DDLFormat;
import org.jkiss.dbeaver.ext.db2.editors.DB2SourceObject;
import org.jkiss.dbeaver.ext.db2.model.cache.DB2RoutineParmsCache;
import org.jkiss.dbeaver.ext.db2.model.dict.*;
import org.jkiss.dbeaver.ext.db2.model.module.DB2Module;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.meta.PropertyLength;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.DBSObjectState;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedure;
import org.jkiss.dbeaver.model.struct.rdb.DBSProcedureType;
import org.jkiss.utils.CommonUtils;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Map;

/**
 * DB2 Routine Base Object (Procedures, Function)
 * 
 * @author Denis Forveille
 */
public class DB2Routine extends DB2Object<DBSObject>
    implements DBSProcedure, DB2SourceObject, DBPRefreshableObject, DBPImageProvider {

    public enum FunctionType {
        C("Column or aggregate"),
        R("Row"),
        S("Scalar"),
        T("Table");

        FunctionType(String type) {
        }
    }

    private final DB2RoutineParmsCache parmsCache = new DB2RoutineParmsCache();

    private String                     fullyQualifiedName;

    private DB2Schema                  db2Schema;

    private DB2RoutineType             type;

    @Nullable
    private String                     specificName;
    private Integer                    routineId;
    private DB2RoutineOrigin           origin;
    private DB2RoutineLanguage         language;
    private String                     dialect;
    private String                     owner;
    private DB2OwnerType               ownerType;
    private String                     text;
    private String                     remarks;

    private Timestamp                  createTime;
    private Timestamp                  alterTime;
    private Timestamp                  lastRegenTime;

    private Integer                    resultSets;
    private String                     parameterStyle;
    private Boolean                    deterministic;
    private String                     externalName;
    private String                     debugMode;

    private String                     jarId;
    private String                     jarSchema;
    private String                     jarSignature;
    private String                     javaClass;
    private DB2RoutineValidType        valid;
    private FunctionType functionType;

    // -----------------------
    // Constructors
    // -----------------------

    public DB2Routine(DBSObject owner, ResultSet dbResult) {
        super(owner, JDBCUtils.safeGetString(dbResult, "ROUTINENAME"), true);

        DB2DataSource db2DataSource = (DB2DataSource) owner.getDataSource();

        this.specificName = JDBCUtils.safeGetString(dbResult, "SPECIFICNAME");
        this.routineId = JDBCUtils.safeGetInteger(dbResult, "ROUTINEID");

        this.type = CommonUtils.valueOf(DB2RoutineType.class, JDBCUtils.safeGetString(dbResult, "ROUTINETYPE"));

        this.origin = CommonUtils.valueOf(DB2RoutineOrigin.class, JDBCUtils.safeGetString(dbResult, "ORIGIN"));
        this.language = CommonUtils.valueOf(DB2RoutineLanguage.class, JDBCUtils.safeGetStringTrimmed(dbResult, "LANGUAGE"));
        this.owner = JDBCUtils.safeGetString(dbResult, DB2Constants.SYSCOLUMN_OWNER);
        this.createTime = JDBCUtils.safeGetTimestamp(dbResult, DB2Constants.SYSCOLUMN_CREATE_TIME);
        this.alterTime = JDBCUtils.safeGetTimestamp(dbResult, DB2Constants.SYSCOLUMN_ALTER_TIME);
        this.lastRegenTime = JDBCUtils.safeGetTimestamp(dbResult, "LAST_REGEN_TIME");
        this.text = JDBCUtils.safeGetString(dbResult, "TEXT");
        this.remarks = JDBCUtils.safeGetString(dbResult, DB2Constants.SYSCOLUMN_REMARKS);

        this.resultSets = JDBCUtils.safeGetInteger(dbResult, "RESULT_SETS");
        this.parameterStyle = JDBCUtils.safeGetString(dbResult, "PARAMETER_STYLE");
        this.deterministic = JDBCUtils.safeGetBoolean(dbResult, "DETERMINISTIC", DB2YesNo.Y.name());
        this.externalName = JDBCUtils.safeGetString(dbResult, "IMPLEMENTATION");
        this.debugMode = JDBCUtils.safeGetString(dbResult, "DEBUG_MODE");

        this.jarId = JDBCUtils.safeGetString(dbResult, "JAR_ID");
        this.jarSchema = JDBCUtils.safeGetString(dbResult, "JARSCHEMA");
        this.jarSignature = JDBCUtils.safeGetString(dbResult, "JAR_SIGNATURE");
        this.javaClass = JDBCUtils.safeGetString(dbResult, "CLASS");
        this.valid = CommonUtils.valueOf(DB2RoutineValidType.class, JDBCUtils.safeGetString(dbResult, DB2Constants.SYSCOLUMN_VALID));

        if (db2DataSource.isAtLeastV9_5()) {
            this.ownerType = CommonUtils.valueOf(DB2OwnerType.class, JDBCUtils.safeGetString(dbResult, DB2Constants.SYSCOLUMN_OWNER_TYPE));
        }
        if (db2DataSource.isAtLeastV9_7()) {
            this.dialect = JDBCUtils.safeGetString(dbResult, "DIALECT");
            if (type == DB2RoutineType.F) {
                this.functionType = CommonUtils.valueOf(FunctionType.class, JDBCUtils.safeGetString(dbResult, "FUNCTIONTYPE"));
            }
        }

        if (owner instanceof DB2Schema) {
            db2Schema = (DB2Schema) owner;
        } else {
            db2Schema = ((DB2Module) owner).getSchema();
        }

        // Compute this once for all
        fullyQualifiedName = DBUtils.getFullQualifiedName(db2DataSource, owner, this);

    }

    public DB2RoutineType getType()
    {
        return type;
    }

    // -----------------
    // Business Contract
    // -----------------
    @NotNull
    @Override
    public DBSObjectState getObjectState()
    {
        return valid == DB2RoutineValidType.Y ? DBSObjectState.NORMAL : DBSObjectState.UNKNOWN;
    }

    @Override
    public void refreshObjectState(@NotNull DBRProgressMonitor monitor) throws DBCException
    {
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException
    {
        parmsCache.clearCache();
        return this;
    }

    @Override
    @Property(viewable = true, order = 7)
    public DBSProcedureType getProcedureType()
    {
        return type.getProcedureType();
    }

    @NotNull
    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context)
    {
        return fullyQualifiedName;
    }

    @Override
    public DBSObjectContainer getContainer()
    {
        if (parent instanceof DBSObjectContainer) {
            return (DBSObjectContainer) parent;
        }
        return db2Schema;
    }

    // -----------------
    // Children
    // -----------------
    @Override
    public Collection<DB2RoutineParm> getParameters(DBRProgressMonitor monitor) throws DBException
    {
        return parmsCache.getAllObjects(monitor, this);
    }

    // -----------------
    // Source
    // -----------------

    @Override
    public String getObjectDefinitionText(DBRProgressMonitor monitor, Map<String, Object> options) throws DBException
    {
        if ((language != null) && (language.equals(DB2RoutineLanguage.SQL))) {
            if (DB2DDLFormat.getCurrentFormat(getDataSource()).needsFormatting()) {
                return DB2Utils.formatSQLProcedureDDL(getDataSource(), text);
            } else {
                return text;
            }
        } else {
            return DB2Messages.no_ddl_for_nonsql_routines;
        }
    }

    // -----------------------
    // Properties
    // -----------------------

    @Property(viewable = true, order = 2)
    public DB2Schema getSchema()
    {
        return db2Schema;
    }

    @Nullable
    @Property(viewable = true, order = 3)
    public String getSpecificName() {
        return specificName;
    }

    @Property(viewable = true, order = 5, category = DB2Constants.CAT_DATETIME)
    public DB2RoutineLanguage getLanguage()
    {
        return language;
    }

    @Property(viewable = true, order = 6)
    public Integer getRoutineId()
    {
        return routineId;
    }

    @Property(viewable = false, order = 10)
    public DB2RoutineValidType getValid()
    {
        return valid;
    }

    @Property(viewable = false, order = 11, category = DB2Constants.CAT_CODE)
    public String getDialect()
    {
        return dialect;
    }

    @Property(viewable = false, order = 12, category = DB2Constants.CAT_CODE)
    public String getParameterStyle()
    {
        return parameterStyle;
    }

    @Property(viewable = false, order = 13, category = DB2Constants.CAT_CODE)
    public Boolean getDeterministic()
    {
        return deterministic;
    }

    @Property(viewable = false, order = 14, category = DB2Constants.CAT_CODE)
    public Integer getResultSets()
    {
        return resultSets;
    }

    @Property(viewable = false, order = 15, category = DB2Constants.CAT_CODE)
    public String getDebugMode()
    {
        return debugMode;
    }

    @Property(viewable = false, order = 20, category = DB2Constants.CAT_CODE)
    public DB2RoutineOrigin getOrigin()
    {
        return origin;
    }

    @Property(viewable = false, order = 21, category = DB2Constants.CAT_CODE)
    public String getExternalName()
    {
        return externalName;
    }

    @Property(viewable = false, order = 22, category = DB2Constants.CAT_CODE)
    public String getJavaClass()
    {
        return javaClass;
    }

    @Property(viewable = false, order = 23, category = DB2Constants.CAT_CODE)
    public String getJarId()
    {
        return jarId;
    }

    @Property(viewable = false, order = 24, category = DB2Constants.CAT_CODE)
    public String getJarSchema()
    {
        return jarSchema;
    }

    @Property(viewable = false, order = 25, category = DB2Constants.CAT_CODE)
    public String getJarSignature()
    {
        return jarSignature;
    }

    @Property(viewable = false, category = DB2Constants.CAT_DATETIME)
    public Timestamp getCreateTime()
    {
        return createTime;
    }

    @Property(viewable = false, category = DB2Constants.CAT_DATETIME)
    public Timestamp getAlterTime()
    {
        return alterTime;
    }

    @Property(viewable = false, category = DB2Constants.CAT_DATETIME)
    public Timestamp getLastRegenTime()
    {
        return lastRegenTime;
    }

    @Property(viewable = false, category = DB2Constants.CAT_OWNER)
    public String getOwner()
    {
        return owner;
    }

    @Property(viewable = false, category = DB2Constants.CAT_OWNER)
    public DB2OwnerType getOwnerType()
    {
        return ownerType;
    }

    @Nullable
    @Override
    @Property(viewable = false, length = PropertyLength.MULTILINE)
    public String getDescription()
    {
        return remarks;
    }

    public FunctionType getFunctionType() {
        return functionType;
    }

    @Nullable
    @Override
    public DBPImage getObjectImage() {
        if (type == DB2RoutineType.F) {
            return DBIcon.TREE_FUNCTION;
        } else {
            return DBIcon.TREE_PROCEDURE;
        }
    }
}
