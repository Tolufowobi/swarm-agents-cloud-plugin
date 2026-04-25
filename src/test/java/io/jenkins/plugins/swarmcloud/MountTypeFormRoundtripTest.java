package io.jenkins.plugins.swarmcloud;

import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate.MountConfig;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate.SwarmMountType;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlOption;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for issue #5: tmpfs host mount gets changed to bind.
 */
@WithJenkins
class MountTypeFormRoundtripTest {

    private static final String CLOUD_NAME = "tmpfs-bug";
    private static final String CLOUD_CONFIG_URL = "manage/cloud/" + CLOUD_NAME + "/configure";

    @Test
    void typeSelectShowsTmpfsAsSelectedInRenderedHtml(JenkinsRule j) throws Exception {
        setupCloudWithTmpfsMount(j);

        WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        wc.getOptions().setThrowExceptionOnScriptError(false);
        HtmlPage page = wc.goTo(CLOUD_CONFIG_URL);
        wc.waitForBackgroundJavaScript(5000);

        HtmlSelect typeSelect = findTypeSelect(page);
        HtmlOption selected = typeSelect.getSelectedOptions().isEmpty()
                ? null
                : typeSelect.getSelectedOptions().get(0);
        assertEquals("TMPFS", selected == null ? null : selected.getValueAttribute(),
                "Rendered <select> must mark TMPFS option as selected when the bound value is TMPFS");
    }

    @Test
    void tmpfsTypeSurvivesUnchangedFormSubmit(JenkinsRule j) throws Exception {
        setupCloudWithTmpfsMount(j);

        WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        wc.getOptions().setThrowExceptionOnScriptError(false);
        HtmlPage page = wc.goTo(CLOUD_CONFIG_URL);
        wc.waitForBackgroundJavaScript(5000);

        HtmlForm form = page.getFormByName("config");
        j.submit(form);

        SwarmCloud reloaded = (SwarmCloud) j.jenkins.clouds.getByName(CLOUD_NAME);
        MountConfig reloadedMount = reloaded.getTemplates().get(0).getMounts().get(0);
        assertEquals(SwarmMountType.TMPFS, reloadedMount.getType(),
                "Mount type must remain TMPFS after submitting the cloud edit form unchanged");
    }

    private SwarmCloud setupCloudWithTmpfsMount(JenkinsRule j) {
        SwarmCloud cloud = new SwarmCloud(CLOUD_NAME);
        cloud.setDockerHost("tcp://localhost:2376");

        SwarmAgentTemplate template = new SwarmAgentTemplate("tmpl");
        template.setImage("jenkins/inbound-agent:latest");

        MountConfig tmpfs = new MountConfig(SwarmMountType.TMPFS, "", "/tmp/cache");
        template.setMounts(List.of(tmpfs));

        cloud.setTemplates(List.of(template));
        j.jenkins.clouds.add(cloud);
        return cloud;
    }

    private HtmlSelect findTypeSelect(HtmlPage page) {
        for (DomElement el : page.getElementsByTagName("select")) {
            if (el instanceof HtmlSelect) {
                HtmlSelect select = (HtmlSelect) el;
                String name = select.getNameAttribute();
                if (name != null && name.contains("type") && select.getOptionSize() > 0) {
                    boolean hasTmpfs = false;
                    for (HtmlOption opt : select.getOptions()) {
                        if ("TMPFS".equals(opt.getValueAttribute())) {
                            hasTmpfs = true;
                            break;
                        }
                    }
                    if (hasTmpfs) {
                        return select;
                    }
                }
            }
        }
        throw new AssertionError("MountConfig type <select> not found on cloud configure page");
    }
}
