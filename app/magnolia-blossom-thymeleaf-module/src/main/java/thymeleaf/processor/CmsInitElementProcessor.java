package thymeleaf.processor;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.cms.i18n.I18nContentSupportFactory;
import info.magnolia.context.MgnlContext;
import info.magnolia.rendering.context.RenderingContext;
import info.magnolia.rendering.template.TemplateDefinition;
import info.magnolia.templating.elements.MarkupHelper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.RepositoryException;

import org.thymeleaf.Arguments;
import org.thymeleaf.dom.Comment;
import org.thymeleaf.dom.Element;
import org.thymeleaf.dom.Node;
import org.thymeleaf.dom.Text;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.processor.attr.AbstractChildrenModifierAttrProcessor;
import org.thymeleaf.standard.expression.StandardExpressionProcessor;

/**
 * Created with IntelliJ IDEA. User: Thomas Date: 10.11.12 Time: 11:57 To change this template use File | Settings |
 * File Templates.
 */
public class CmsInitElementProcessor extends AbstractChildrenModifierAttrProcessor {

    private final I18nContentSupport i18nContentSupport = I18nContentSupportFactory.getI18nSupport();
    private static final String CMS_PAGE_TAG = "cms:page";
    public static final String CONTENT_ATTRIBUTE = "content";

    private static final String[] js = new String[] { "/.magnolia/pages/javascript.js",
            "/.magnolia/pages/messages.en.js", "/.resources/admin-js/dialogs/dialogs.js",
            "/.resources/calendar/calendar.js", "/.resources/calendar/calendar-setup.js",
            "/.resources/editor/info.magnolia.templating.editor.PageEditor/info.magnolia.templating.editor.PageEditor.nocache.js" };
    private static final String[] css = new String[] { "/.resources/admin-css/admin-all.css",
            "/.resources/magnolia-templating-editor/css/editor.css", "/.resources/calendar/skins/aqua/theme.css" };

    public CmsInitElementProcessor() {
        super("init");
    }

    @Override
    protected List<Node> getModifiedChildren(final Arguments arguments, final Element element,
            final String attributeName)

    {
        // if (!isAdmin()) {
        // return element.getChildren();
        // }

        final String name = element.getNormalizedName();
        if (!"head".equals(name)) {
            throw new TemplateProcessingException("cms:init is only allowed on head element");
        }
        final List<Node> result = new ArrayList<>(element.getChildren());
        Element el = new Element("meta");
        el.setAttribute("gwt:property", "locale=" + i18nContentSupport.getLocale());
        result.add(el);
        final String ctx = MgnlContext.getContextPath();
        for (final String sheet : css) {
            el = new Element("link");
            el.setAttribute("rel", "stylesheet");
            el.setAttribute("type", "text/css");
            el.setAttribute("href", ctx + sheet);
            result.add(el);
            final Text t = new Text("\n");
            result.add(t);
        }
        for (final String script : js) {
            el = new Element("script");
            el.setAttribute("type", "text/javascript");
            el.setAttribute("src", ctx + script);
            result.add(el);
            final Text t = new Text("\n");
            result.add(t);
        }
        el = new Element("script");
        el.setAttribute("type", "text/javascript");
        el.setAttribute("src", ctx + "/.resources/calendar/lang/calendar-" + MgnlContext.getLocale().getLanguage()
                + ".js");
        result.add(el);
        Text t = new Text("\n");
        result.add(t);
        final StringWriter writer = new StringWriter();
        final MarkupHelper helper = new MarkupHelper(writer);
        try {
            helper.append(" " + CMS_PAGE_TAG);
            final Object nodeObj = StandardExpressionProcessor.processExpression(arguments, "${content}");
            if (!(nodeObj instanceof javax.jcr.Node)) {
                throw new TemplateProcessingException("Musst pass a javx.jcr.Node here");
            }
            final javax.jcr.Node node = (javax.jcr.Node) nodeObj;
            if (node != null) {
                helper.attribute(CONTENT_ATTRIBUTE, getNodePath(node));
            }
            final Object ctxObj = StandardExpressionProcessor.processExpression(arguments, "${renderingContext}");
            if (!(ctxObj instanceof RenderingContext)) {
                throw new TemplateProcessingException("Musst pass a RenderingContext here");
            }

            final RenderingContext renderingContext = (RenderingContext) ctxObj;
            final TemplateDefinition templateDefinition = (TemplateDefinition) renderingContext
                    .getRenderableDefinition();
            final String dlg = templateDefinition.getDialog();
            if (dlg != null) {
                helper.attribute("dialog", dlg);
            }
            helper.attribute("preview", String.valueOf(MgnlContext.getAggregationState().isPreviewMode()));

            // here we provide the page editor with the available locales and their respective URI for the current page
            // if (i18nAuthoringSupport.isEnabled() && i18nContentSupport.isEnabled() &&
            // i18nContentSupport.getLocales().size()>1){
            //
            // Content currentPage = MgnlContext.getAggregationState().getMainContent();
            // String currentUri = createURI(currentPage, i18nContentSupport.getLocale());
            // helper.attribute("currentURI", currentUri);
            //
            // List<String> availableLocales = new ArrayList<String>();
            //
            // for (Locale locale : i18nContentSupport.getLocales()) {
            // String uri = createURI(currentPage, locale);
            // String label = StringUtils.capitalize(locale.getDisplayLanguage(locale));
            // if(StringUtils.isNotEmpty(locale.getCountry())){
            // label += " (" + StringUtils.capitalize(locale.getDisplayCountry()) + ")";
            // }
            // availableLocales.add(label + ":" + uri);
            // }
            //
            // helper.attribute("availableLocales", StringUtils.join(availableLocales, ","));
            // }

        } catch (final IOException e) {
            throw new TemplateProcessingException("comment", e);
        }

        Comment comment = new Comment(writer.toString());

        result.add(comment);
        t = new Text("\n");
        result.add(t);
        comment = new Comment(" /" + CMS_PAGE_TAG + " ");
        result.add(comment);
        t = new Text("\n");
        result.add(t);

        return result;
    }

    @Override
    public int getPrecedence() {
        return 1000; // To change body of implemented methods use File | Settings | File Templates.
    }

    protected String getNodePath(final javax.jcr.Node node) throws TemplateProcessingException {
        try {
            return node.getSession().getWorkspace().getName() + ":" + node.getPath();
        } catch (final RepositoryException e) {
            throw new TemplateProcessingException("Can't construct node path for node " + node);
        }
    }
}
