package thymeleaf.controller.templates;

import info.magnolia.module.blossom.annotation.*;
import info.magnolia.ui.form.config.TabBuilder;
import info.magnolia.ui.framework.config.UiConfig;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import thymeleaf.controller.components.ThymeleafComponent;
import thymeleaf.controller.components.ThymeleafComponent2;

/**
 * User: Thomas
 * Date: 10.11.12
 * Time: 08:05
 * To change this template use File | Settings | File Templates.
 */
@Template(id = "thymeleaf_proto:pages/mainTemplate", title = "Main Template")
@Controller
public class MainTemplate {

    @RequestMapping("/mainTemplate")
    public String handleRequest() {
        return "templates/main.html";
    }

    @TabFactory("Properties")
    public void createTab(UiConfig cfg,TabBuilder tab) {
        tab.fields(
                cfg.fields.text("test").label("Test")
        );
    }



    @Area("Area")
    @Inherits
    @AvailableComponentClasses({ThymeleafComponent.class, ThymeleafComponent2.class})
    @Controller
    public static class PromosArea {

        @RequestMapping("/mainTemplate/promos")
        public String render() {

            return "areas/area.html :: mainArea";
        }
    }
}
