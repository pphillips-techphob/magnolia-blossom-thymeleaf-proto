package thymeleaf.processor;

import info.magnolia.context.MgnlContext;
import info.magnolia.module.blossom.support.IncludeRequestWrapper;
import info.magnolia.module.blossom.template.BlossomAreaDefinition;
import info.magnolia.module.blossom.template.BlossomTemplateDefinition;
import info.magnolia.module.blossom.template.HandlerMetaData;
import info.magnolia.objectfactory.Components;
import info.magnolia.rendering.context.RenderingContext;
import info.magnolia.rendering.engine.RenderingEngine;
import info.magnolia.rendering.template.AreaDefinition;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.Arguments;
import org.thymeleaf.dom.Comment;
import org.thymeleaf.dom.Element;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.processor.ProcessorResult;

import thymeleaf.blossom.ThymeleafTemplateExporter;
import thymeleaf.magnolia.ThymeleafAreaElement;

/**
 * Created with IntelliJ IDEA. User: tkratz Date: 11.11.12 Time: 09:39 To change this template use File | Settings |
 * File Templates.
 */
public class CmsAreaElementProcessor extends AbstractRecursiveInclusionProcessor {

    public static final String ATTR_NAME = "area";

    private final ThymeleafTemplateExporter templateExporter;
    private List<HandlerMapping> handlerMappings;
    private List<HandlerAdapter> handlerAdapters;

    public CmsAreaElementProcessor(final ApplicationContext ctx, final ServletContext sctx) {

        super(ctx, sctx, ATTR_NAME);
        this.templateExporter = ctx.getBean(ThymeleafTemplateExporter.class);
        this.context = ctx;
        this.servletContext = sctx;

        initGHandlerAdapters();
        initHandlerMappings();
    }

    private void initHandlerMappings() {
        final Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
                HandlerMapping.class, true, false);
        if (!matchingBeans.isEmpty()) {
            this.handlerMappings = new ArrayList<>(matchingBeans.values());
            // We keep HandlerMappings in sorted order.
            OrderComparator.sort(this.handlerMappings);
        }
    }

    private void initGHandlerAdapters() {
        final Map<String, HandlerAdapter> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context,
                HandlerAdapter.class, true, false);
        if (!matchingBeans.isEmpty()) {
            this.handlerAdapters = new ArrayList<>(matchingBeans.values());
            // We keep HandlerAdapters in sorted order.
            OrderComparator.sort(this.handlerAdapters);
        }
    }

    private ThymeleafAreaElement createAreaElement(final RenderingContext renderingContext) {

        return Components.getComponentProvider().newInstance(ThymeleafAreaElement.class, renderingContext);
    }

    @Override
    public int getPrecedence() {
        return 1000;
    }

    protected boolean getSubstituteInclusionNode(final Arguments arguments, final Element element,
            final String attributeName, final String attributeValue) {
        // th:include does not substitute the inclusion node
        return true;
    }

    @Override
    public final ProcessorResult processAttribute(final Arguments arguments, final Element element,
            final String attributeName) {

        final HttpServletRequest request = MgnlContext.getWebContext().getRequest();
        final HttpServletResponse response = MgnlContext.getWebContext().getResponse();

        final String attributeValue = element.getAttributeValue(attributeName);

        final RenderingEngine renderingEngine = Components.getComponent(RenderingEngine.class);
        final RenderingContext renderingContext = renderingEngine.getRenderingContext();

        AreaDefinition areaDef = null;
        BlossomTemplateDefinition templateDefinition;
        try {

            templateDefinition = (BlossomTemplateDefinition) renderingContext.getRenderableDefinition();
            if (templateDefinition.getAreas().containsKey(attributeValue)) {
                areaDef = templateDefinition.getAreas().get(attributeValue);
            }

        } catch (final ClassCastException x) {

            throw new TemplateProcessingException("Only Blossom, templates supported", x);
        }

        if (areaDef == null) {
            throw new TemplateProcessingException("Area not found:" + attributeValue);
        }

        Object handlerBean = null;
        final String path = ((BlossomAreaDefinition) areaDef).getHandlerPath();
        for (final HandlerMetaData meta : templateExporter.getDetectedHandlers().getTemplates()) {

            // make sure we get the right area def from out template
            if (meta.getHandlerPath().equals(templateDefinition.getHandlerPath())) {
                final List<HandlerMetaData> areasByEnclosingClass = templateExporter.getDetectedHandlers()
                        .getAreasByEnclosingClass(meta.getHandlerClass());
                if (areasByEnclosingClass != null) {
                    for (final HandlerMetaData areaMeta : areasByEnclosingClass) {

                        if (areaMeta.getHandlerPath().equals(path)) {
                            handlerBean = areaMeta.getHandler();
                            break;
                        }
                    }
                }
                break;
            }
        }
        if (handlerBean == null) {
            throw new TemplateProcessingException("Handler not found");
        }

        HandlerExecutionChain chain = null;
        try {
            for (final HandlerMapping hm : this.handlerMappings) {
                final HandlerExecutionChain handler = hm.getHandler(request);
                if (handler != null) {
                    chain = handler;
                    break;
                }
            }
        } catch (final Exception e) {
            throw new TemplateProcessingException("Cannot find handler", e);
        }
        if (chain == null) {
            throw new TemplateProcessingException("Handler not found " + handlerBean.getClass().getName());
        }
        HandlerAdapter adapter = null;
        for (final HandlerAdapter ha : this.handlerAdapters) {

            if (ha.supports(handlerBean)) {
                adapter = ha;
                break;
            }
        }
        if (adapter == null) {
            throw new TemplateProcessingException("Na HandlerAdapter found");

        }

        final ThymeleafAreaElement areaElement = createAreaElement(renderingContext);
        areaElement.setName(areaDef.getName());
        final StringWriter out = new StringWriter();
        try {
            areaElement.begin(out);
        } catch (final Exception e) {
            throw new TemplateProcessingException("render comment", e);
        }
        String comment = out.toString();
        if (comment.startsWith("<!--")) {
            comment = comment.substring(4);
        }
        if (comment.endsWith("-->\n")) {
            comment = comment.substring(0, comment.length() - 4);
        }
        final Comment commentNode = new Comment(comment);

        final Map<String, Object> vars = areaElement.getContextMap();

        ModelAndView mv = null;
        MgnlContext.getWebContext().push(request, response);
        final IncludeRequestWrapper wrapper = new IncludeRequestWrapper(request, MgnlContext.getContextPath() + path,
                MgnlContext.getContextPath(), path, null, request.getQueryString());
        for (final String key : vars.keySet()) {
            wrapper.setSpecialAttribute(key, vars.get(key));
        }
        try {
            mv = adapter.handle(wrapper, response, handlerBean);
        } catch (final Exception e) {
            throw new TemplateProcessingException("Spring handler error", e);
        } finally {
            MgnlContext.getWebContext().pop();
        }
        final String template = mv.getViewName();

        final String documentName = areaDef.getName();
        final ProcessorResult result = doRecursiveProcessing(arguments, element, attributeName, attributeValue,
                template, commentNode, vars, documentName, " /cms:area ");

        return result;

    }

}
