package org.jenkinsci.plugins.buildwithparameters;


import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.PasswordParameterValue;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BuildWithParametersAction implements Action {
    private final AbstractProject project;

    public BuildWithParametersAction(AbstractProject project) {
        this.project = project;
    }


    //////////////////
    //              //
    //     VIEW     //
    //              //
    //////////////////

    public String getProjectName() {
        return project.getName();
    }

    public List<BuildParameter> getAvailableParameters() {
        List<BuildParameter> buildParameters = new ArrayList<BuildParameter>();

        for (ParameterDefinition parameterDefinition : getParameterDefinitions()) {
            BuildParameter buildParameter = new BuildParameter(parameterDefinition.getName(), parameterDefinition.getDescription());
            buildParameter.setPasswordParam(parameterDefinition.getClass().isAssignableFrom(PasswordParameterDefinition.class));

            try {
                buildParameter.setValue(getParameterDefinitionValue(parameterDefinition));
            } catch (IllegalArgumentException ignored) {
                // If a value was provided that does not match available options, leave the value blank.
            }

            buildParameters.add(buildParameter);
        }

        return buildParameters;
    }

    ParameterValue getParameterDefinitionValue(ParameterDefinition parameterDefinition) {
        return parameterDefinition.createValue(Stapler.getCurrentRequest());
    }

    public String getIconFileName() {
        return "clock.png";
    }

    public String getDisplayName() {
        return "Build With Params";
    }

    public String getUrlName() {
        return "parambuild";
    }


    //////////////////
    //              //
    //  SUBMISSION  //
    //              //
    //////////////////

    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        project.checkPermission(AbstractProject.BUILD);

        List<ParameterValue> values = new ArrayList<ParameterValue>();

        JSONObject formData = req.getSubmittedForm();
        if (!formData.isEmpty()) {
            for (ParameterDefinition parameterDefinition : getParameterDefinitions()) {
                ParameterValue parameterValue = parameterDefinition.createValue(req);
                if(parameterValue.getClass().isAssignableFrom(PasswordParameterValue.class)) {
                    parameterValue = applyDefaultPassword(parameterDefinition, parameterValue);
                }
                // This will throw an exception if the provided value is not a valid option for the parameter.
                // This is the desired behavior, as we want to ensure valid submissions.
                values.add(parameterValue);
            }
        }

        Hudson.getInstance().getQueue().schedule(project, 0, new ParametersAction(values), new CauseAction(new Cause.UserIdCause()));
        rsp.sendRedirect("../");
    }


    ParameterValue applyDefaultPassword(ParameterDefinition parameterDefinition,
            ParameterValue parameterValue) {
        String jobPassword = getPasswordValue((PasswordParameterValue)parameterValue);
        if(!BuildParameter.isDefaultPasswordPlaceholder(jobPassword)) {
            return parameterValue;
        }
        String jobDefaultPassword = getPasswordValue(((PasswordParameterValue) parameterDefinition.getDefaultParameterValue()));
        ParameterValue passwordParameterValue = new PasswordParameterValue(parameterValue.getName(), jobDefaultPassword);
        return passwordParameterValue;
    }


    static String getPasswordValue(PasswordParameterValue parameterValue) {
        Secret secret = parameterValue.getValue();
        return Secret.toString(secret);
    }


    //////////////////
    //              //
    //   HELPERS    //
    //              //
    //////////////////

    private List<ParameterDefinition> getParameterDefinitions() {
        ParametersDefinitionProperty property = (ParametersDefinitionProperty) project.getProperty(ParametersDefinitionProperty.class);
        if (property != null && property.getParameterDefinitions() != null) {
            return property.getParameterDefinitions();
        }
        return new ArrayList<ParameterDefinition>();
    }

}
