package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.FileNotFoundException;

/**
 * Insert default test suite.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "insert-test", defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES, requiresDependencyResolution = ResolutionScope.TEST)
public class TestInsertionMojo extends AbstractJenkinsMojo {

    /**
     * If true, the automatic test injection will be skipped.
     */
    @Parameter(property = "maven-hpi-plugin.disabledTestInjection", defaultValue = "false")
    private boolean disabledTestInjection;

    /**
     * Name of the injected test.
     *
     * You may change this to "InjectIT" to get the test running during phase integration-test.
     */
    @Parameter(property = "maven-hpi-plugin.injectedTestName", defaultValue = "InjectedTest")
    private String injectedTestName;

    /**
     * If true, verify that all the jelly scripts have the Jelly XSS PI in them.
     */
    @Parameter(property = "jelly.requirePI")
    private boolean requirePI;

    private static String quote(String s) {
        return '"'+s.replace("\\", "\\\\")+'"';
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (disabledTestInjection) {
            getLog().info("Skipping auto-test generation");
            return;
        }

        String target = findJenkinsVersion();
        if (new VersionNumber(target).compareTo(new VersionNumber("1.327"))<0) {
            getLog().info("Skipping auto-test generation because we are targeting Jenkins "+target+" (at least 1.327 is required).");
            return;
        }

        try {
            File f = new File(project.getBasedir(), "target/inject-tests");
            f.mkdirs();
            File javaFile = new File(f, injectedTestName + ".java");
            PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(javaFile)));
            w.println("import java.util.*;");
            w.println("/**");
            w.println(" * Entry point to auto-generated tests (generated by maven-hpi-plugin).");
            w.println(" * If this fails to compile, you are probably using Hudson &lt; 1.327. If so, disable");
            w.println(" * this code generation by configuring maven-hpi-plugin to &lt;disabledTestInjection>true&lt;/disabledTestInjection>.");
            w.println(" */");
            w.println("public class " + injectedTestName + " extends junit.framework.TestCase {");
            w.println("  public static junit.framework.Test suite() throws Exception {");
            w.println("    Map parameters = new HashMap();");
            w.println("    parameters.put(\"basedir\","+quote(project.getBasedir().getAbsolutePath())+");");
            w.println("    parameters.put(\"artifactId\","+quote(project.getArtifactId())+");");
            w.println("    parameters.put(\"outputDirectory\","+quote(project.getBuild().getOutputDirectory())+");");
            w.println("    parameters.put(\"testOutputDirectory\","+quote(project.getBuild().getTestOutputDirectory())+");");
            w.println("    parameters.put(\"requirePI\","+quote(String.valueOf(requirePI))+");");
            w.println("    return new org.jvnet.hudson.test.PluginAutomaticTestBuilder().build(parameters);");
            w.println("  }");
            w.println("}");
            w.close();

            project.addTestCompileSourceRoot(f.getAbsolutePath());

            // always set the same time stamp on this file, so that Maven will not re-compile this
            // every time we run this mojo.
            javaFile.setLastModified(0);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
