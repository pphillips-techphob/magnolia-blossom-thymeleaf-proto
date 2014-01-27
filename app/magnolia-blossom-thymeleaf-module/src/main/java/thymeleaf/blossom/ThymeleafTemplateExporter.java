package thymeleaf.blossom;

import info.magnolia.module.blossom.annotation.Area;
import info.magnolia.module.blossom.annotation.Template;
import info.magnolia.module.blossom.dialog.BlossomDialogDefinitionProvider;
import info.magnolia.module.blossom.dialog.BlossomDialogDescription;
import info.magnolia.module.blossom.dialog.DialogDescriptionBuilder;
import info.magnolia.module.blossom.dispatcher.BlossomDispatcher;
import info.magnolia.module.blossom.dispatcher.BlossomDispatcherAware;
import info.magnolia.module.blossom.dispatcher.BlossomDispatcherInitializedEvent;
import info.magnolia.module.blossom.support.AbstractUrlMappedHandlerPostProcessor;
import info.magnolia.module.blossom.template.BlossomAreaDefinition;
import info.magnolia.module.blossom.template.BlossomTemplateDefinition;
import info.magnolia.module.blossom.template.BlossomTemplateDefinitionProvider;
import info.magnolia.module.blossom.template.DetectedHandlersMetaData;
import info.magnolia.module.blossom.template.HandlerMetaData;
import info.magnolia.module.blossom.template.TemplateDefinitionBuilder;
import info.magnolia.objectfactory.Components;
import info.magnolia.rendering.template.AreaDefinition;
import info.magnolia.rendering.template.registry.TemplateDefinitionRegistry;
import info.magnolia.ui.dialog.registry.DialogDefinitionRegistry;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationListener;

/**
 * Created with IntelliJ IDEA. User: tkratz Date: 11.11.12 Time: 10:59 To change this template use File | Settings |
 * File Templates.
 */
public class ThymeleafTemplateExporter extends AbstractUrlMappedHandlerPostProcessor implements InitializingBean,
        ApplicationListener<BlossomDispatcherInitializedEvent>, BlossomDispatcherAware {

    private static final String TEMPLATE_DIALOG_PREFIX = "blossom-template-dialog:";
    private static final String AREA_DIALOG_PREFIX = "blossom-area-dialog:";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BlossomDispatcher dispatcher;
    private TemplateDefinitionBuilder templateDefinitionBuilder;
    private DialogDescriptionBuilder dialogDescriptionBuilder;

    private final DetectedHandlersMetaData detectedHandlers = new DetectedHandlersMetaData();

    public void setTemplateDefinitionBuilder(final TemplateDefinitionBuilder templateDefinitionBuilder) {
        this.templateDefinitionBuilder = templateDefinitionBuilder;
    }

    public void setDialogDescriptionBuilder(final DialogDescriptionBuilder dialogDescriptionBuilder) {
        this.dialogDescriptionBuilder = dialogDescriptionBuilder;
    }

    @Override
    public void setBlossomDispatcher(final BlossomDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    protected void postProcessHandler(final Object handler, final String handlerPath) {
        final Class<?> handlerClass = AopUtils.getTargetClass(handler);
        if (handlerClass.isAnnotationPresent(Area.class)) {
            detectedHandlers.addArea(new HandlerMetaData(handler, handlerPath, handlerClass));
        } else if (handlerClass.isAnnotationPresent(Template.class)) {
            detectedHandlers.addTemplate(new HandlerMetaData(handler, handlerPath, handlerClass));
        }
    }

    @Override
    public void onApplicationEvent(final BlossomDispatcherInitializedEvent event) {
        if (event.getSource() == dispatcher) {
            exportTemplates();
        }
    }

    protected void exportTemplates() {
        for (final HandlerMetaData template : detectedHandlers.getTemplates()) {

            final BlossomTemplateDefinition definition = templateDefinitionBuilder.buildTemplateDefinition(dispatcher,
                    detectedHandlers, template);

            Components.getComponent(TemplateDefinitionRegistry.class).register(
                    new BlossomTemplateDefinitionProvider(definition));

            if (StringUtils.isEmpty(definition.getDialog())) {
                registerTemplateDialog(definition);
            }

            registerDialogFactories(definition);

            registerAreaDialogs(definition.getAreas().values());
        }

    }

    protected void registerDialogFactories(final BlossomTemplateDefinition templateDefinition) {

        final List<BlossomDialogDescription> dialogDescriptions = dialogDescriptionBuilder
                .buildDescriptions(templateDefinition.getHandler());
        for (final BlossomDialogDescription dialogDescription : dialogDescriptions) {

            Components.getComponent(DialogDefinitionRegistry.class).register(
                    new BlossomDialogDefinitionProvider(dialogDescription));

            if (logger.isDebugEnabled()) {
                logger.debug("Registered dialog factory within template [" + templateDefinition.getId() + "] with id ["
                        + dialogDescription.getId() + "]");
            }
        }
    }

    protected void registerTemplateDialog(final BlossomTemplateDefinition templateDefinition) {

        final String templateId = templateDefinition.getId();

        final String dialogId = TEMPLATE_DIALOG_PREFIX
                + AopUtils.getTargetClass(templateDefinition.getHandler()).getName();

        final BlossomDialogDescription dialogDescription = dialogDescriptionBuilder.buildDescription(dialogId,
                templateDefinition.getTitle(), templateDefinition.getHandler());

        if (dialogDescription.getFactoryMetaData().isEmpty()) {
            return;
        }

        templateDefinition.setDialog(dialogId);

        Components.getComponent(DialogDefinitionRegistry.class).register(
                new BlossomDialogDefinitionProvider(dialogDescription));

        if (logger.isDebugEnabled()) {
            logger.debug("Registered dialog for template [" + templateId + "] with id [" + dialogId + "]");
        }
    }

    protected void registerAreaDialogs(final Collection<AreaDefinition> areas) {
        for (final AreaDefinition areaDefinition : areas) {
            if (StringUtils.isEmpty(areaDefinition.getDialog())) {
                registerAreaDialog((BlossomAreaDefinition) areaDefinition);
            }
            registerAreaDialogs(areaDefinition.getAreas().values());
        }
    }

    protected void registerAreaDialog(final BlossomAreaDefinition areaDefinition) {

        final String areaName = areaDefinition.getName();

        final String dialogId = AREA_DIALOG_PREFIX + AopUtils.getTargetClass(areaDefinition.getHandler()).getName();

        final BlossomDialogDescription dialogDescription = dialogDescriptionBuilder.buildDescription(dialogId,
                areaDefinition.getTitle(), areaDefinition.getHandler());

        if (dialogDescription.getFactoryMetaData().isEmpty()) {
            return;
        }

        areaDefinition.setDialog(dialogId);

        Components.getComponent(DialogDefinitionRegistry.class).register(
                new BlossomDialogDefinitionProvider(dialogDescription));

        if (logger.isDebugEnabled()) {
            logger.debug("Registered dialog for area [" + areaName + "] with id [" + dialogId + "]");
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (templateDefinitionBuilder == null) {
            templateDefinitionBuilder = new TemplateDefinitionBuilder();
        }
        if (dialogDescriptionBuilder == null) {
            dialogDescriptionBuilder = new DialogDescriptionBuilder();
        }
    }

    public DetectedHandlersMetaData getDetectedHandlers() {
        return detectedHandlers;
    }

}
