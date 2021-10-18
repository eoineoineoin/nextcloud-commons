package it.niedermann.android.markdown.markwon.plugins;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.util.Range;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import junit.framework.TestCase;

import org.commonmark.node.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.SpannableBuilder;
import io.noties.markwon.ext.tasklist.TaskListSpan;
import it.niedermann.android.markdown.markwon.span.InterceptedURLSpan;
import it.niedermann.android.markdown.markwon.span.ToggleTaskListSpan;

@RunWith(RobolectricTestRunner.class)
public class ToggleableTaskListPluginTest extends TestCase {

    @Test
    public void testAfterRender() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        final var node = mock(Node.class);
        final var visitor = mock(MarkwonVisitor.class);

        final var markerSpanConstructor = ToggleableTaskListPlugin.ToggleMarkerSpan.class.getDeclaredConstructor(TaskListSpan.class);
        markerSpanConstructor.setAccessible(true);

        final var builder = new SpannableBuilder("Lorem Ipsum Dolor \nSit Amet");
        builder.setSpan(markerSpanConstructor.newInstance(mock(TaskListSpan.class)), 0, 6);
        builder.setSpan(new URLSpan(""), 6, 11);
        builder.setSpan(markerSpanConstructor.newInstance(mock(TaskListSpan.class)), 11, 19);
        builder.setSpan(new InterceptedURLSpan(Collections.emptyList(), ""), 19, 22);
        builder.setSpan(markerSpanConstructor.newInstance(mock(TaskListSpan.class)), 22, 27);

        when(visitor.builder()).thenReturn(builder);

        final var plugin = new ToggleableTaskListPlugin((i, b) -> {
            // Do nothing...
        });
        plugin.afterRender(node, visitor);

        // We ignore marker spans in this test. They will be removed in another step
        final var spans = builder.getSpans(0, builder.length())
                .stream()
                .filter(span -> span.what.getClass() != ToggleableTaskListPlugin.ToggleMarkerSpan.class)
                .sorted((o1, o2) -> o1.start - o2.start)
                .collect(Collectors.toList());

        assertEquals(5, spans.size());
        assertEquals(ToggleTaskListSpan.class, spans.get(0).what.getClass());
        assertEquals(0, spans.get(0).start);
        assertEquals(6, spans.get(0).end);
        assertEquals(URLSpan.class, spans.get(1).what.getClass());
        assertEquals(6, spans.get(1).start);
        assertEquals(11, spans.get(1).end);
        assertEquals(ToggleTaskListSpan.class, spans.get(2).what.getClass());
        assertEquals(11, spans.get(2).start);
        assertEquals(19, spans.get(2).end);
        assertEquals(InterceptedURLSpan.class, spans.get(3).what.getClass());
        assertEquals(19, spans.get(3).start);
        assertEquals(22, spans.get(3).end);
        assertEquals(ToggleTaskListSpan.class, spans.get(4).what.getClass());
        assertEquals(22, spans.get(4).start);
        assertEquals(27, spans.get(4).end);
    }

    @Test
    public void testAfterSetText() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {
        final var markerSpanConstructor = ToggleableTaskListPlugin.ToggleMarkerSpan.class.getDeclaredConstructor(TaskListSpan.class);
        markerSpanConstructor.setAccessible(true);

        final var editable = new SpannableStringBuilder("Lorem Ipsum Dolor \nSit Amet");
        editable.setSpan(markerSpanConstructor.newInstance(mock(TaskListSpan.class)), 0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(new URLSpan(""), 6, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(markerSpanConstructor.newInstance(mock(TaskListSpan.class)), 11, 19, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(new InterceptedURLSpan(Collections.emptyList(), ""), 19, 22, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(markerSpanConstructor.newInstance(mock(TaskListSpan.class)), 22, 27, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        final var textView = new TextView(ApplicationProvider.getApplicationContext());
        textView.setText(editable);

        assertEquals(3, ((Spanned) textView.getText()).getSpans(0, textView.getText().length(), ToggleableTaskListPlugin.ToggleMarkerSpan.class).length);

        final var plugin = new ToggleableTaskListPlugin((i, b) -> {
            // Do nothing...
        });
        plugin.afterSetText(textView);

        assertEquals(0, ((Spanned) textView.getText()).getSpans(0, textView.getText().length(), ToggleableTaskListPlugin.ToggleMarkerSpan.class).length);
    }

    @Test
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public void testGetSortedSpans() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final var method = ToggleableTaskListPlugin.class.getDeclaredMethod("getSortedSpans", SpannableBuilder.class, Class.class, int.class, int.class);
        method.setAccessible(true);

        final var firstClickableSpan = new URLSpan("");
        final var secondClickableSpan = new InterceptedURLSpan(Collections.emptyList(), "");
        final var unclickableSpan = new ForegroundColorSpan(android.R.color.white);

        final var spannable = new SpannableBuilder("Lorem Ipsum Dolor \nSit Amet");
        spannable.setSpan(firstClickableSpan, 6, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(secondClickableSpan, 19, 22, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(unclickableSpan, 3, 20, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        List<SpannableBuilder.Span> clickableSpans;

        clickableSpans = (List<SpannableBuilder.Span>) method.invoke(null, spannable, ClickableSpan.class, 0, 0);
        assertEquals(0, clickableSpans.size());

        clickableSpans = (List<SpannableBuilder.Span>) method.invoke(null, spannable, ClickableSpan.class, spannable.length() - 1, spannable.length() - 1);
        assertEquals(0, clickableSpans.size());

        clickableSpans = (List<SpannableBuilder.Span>) method.invoke(null, spannable, ClickableSpan.class, 0, 5);
        assertEquals(0, clickableSpans.size());

        clickableSpans = (List<SpannableBuilder.Span>) method.invoke(null, spannable, ClickableSpan.class, 0, spannable.length());
        assertEquals(2, clickableSpans.size());
        assertEquals(firstClickableSpan, clickableSpans.get(0).what);
        assertEquals(secondClickableSpan, clickableSpans.get(1).what);

        clickableSpans = (List<SpannableBuilder.Span>) method.invoke(null, spannable, ClickableSpan.class, 0, 17);
        assertEquals(1, clickableSpans.size());
        assertEquals(firstClickableSpan, clickableSpans.get(0).what);

        clickableSpans = (List<SpannableBuilder.Span>) method.invoke(null, spannable, ClickableSpan.class, 12, 22);
        assertEquals(1, clickableSpans.size());
        assertEquals(secondClickableSpan, clickableSpans.get(0).what);

        clickableSpans = (List<SpannableBuilder.Span>) method.invoke(null, spannable, ClickableSpan.class, 9, 20);
        assertEquals(2, clickableSpans.size());
        assertEquals(firstClickableSpan, clickableSpans.get(0).what);
        assertEquals(secondClickableSpan, clickableSpans.get(1).what);
    }

    @Test
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public void testFindFreeRanges() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final var method = ToggleableTaskListPlugin.class.getDeclaredMethod("findFreeRanges", SpannableBuilder.class, int.class, int.class);
        method.setAccessible(true);

        final var firstClickableSpan = new URLSpan("");
        final var secondClickableSpan = new InterceptedURLSpan(Collections.emptyList(), "");
        var spannable = new SpannableBuilder("Lorem Ipsum Dolor \nSit Amet");
        spannable.setSpan(firstClickableSpan, 6, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(secondClickableSpan, 19, 22, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        List<Range<Integer>> freeRanges;

        freeRanges = (List<Range<Integer>>) method.invoke(null, spannable, 0, 0);
        assertEquals(0, freeRanges.size());

        freeRanges = (List<Range<Integer>>) method.invoke(null, spannable, spannable.length() - 1, spannable.length() - 1);
        assertEquals(0, freeRanges.size());

        freeRanges = (List<Range<Integer>>) method.invoke(null, spannable, 0, 6);
        assertEquals(1, freeRanges.size());
        assertEquals(0, (int) freeRanges.get(0).getLower());
        assertEquals(6, (int) freeRanges.get(0).getUpper());

        freeRanges = (List<Range<Integer>>) method.invoke(null, spannable, 0, 6);
        assertEquals(1, freeRanges.size());
        assertEquals(0, (int) freeRanges.get(0).getLower());
        assertEquals(6, (int) freeRanges.get(0).getUpper());

        freeRanges = (List<Range<Integer>>) method.invoke(null, spannable, 3, 15);
        assertEquals(2, freeRanges.size());
        assertEquals(3, (int) freeRanges.get(0).getLower());
        assertEquals(6, (int) freeRanges.get(0).getUpper());
        assertEquals(11, (int) freeRanges.get(1).getLower());
        assertEquals(15, (int) freeRanges.get(1).getUpper());

        freeRanges = (List<Range<Integer>>) method.invoke(null, spannable, 0, spannable.length());
        assertEquals(3, freeRanges.size());
        assertEquals(0, (int) freeRanges.get(0).getLower());
        assertEquals(6, (int) freeRanges.get(0).getUpper());
        assertEquals(11, (int) freeRanges.get(1).getLower());
        assertEquals(19, (int) freeRanges.get(1).getUpper());
        assertEquals(22, (int) freeRanges.get(2).getLower());
        assertEquals(27, (int) freeRanges.get(2).getUpper());


        // https://github.com/stefan-niedermann/nextcloud-notes/issues/1326

        spannable = new SpannableBuilder("Test Crash\n\nJust text with link https://github.com");
        spannable.setSpan(firstClickableSpan, 32, 50, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(secondClickableSpan, 32, 50, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        freeRanges = (List<Range<Integer>>) method.invoke(null, spannable, 12, 50);
        assertEquals(1, freeRanges.size());
    }
}
