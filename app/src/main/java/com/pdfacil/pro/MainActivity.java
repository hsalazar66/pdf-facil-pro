package com.pdfacil.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** PDF Útil Pro HS beta: funciona localmente y no utiliza servidores externos. */
public class MainActivity extends Activity {
    private static final int REQUEST_OPEN = 101;
    private static final int REQUEST_IMAGES = 102;
    private static final int REQUEST_MERGE = 103;
    private static final int REQUEST_SAVE = 104;
    private static final int REQUEST_CAMERA = 105;
    private static final int BG = Color.rgb(7, 18, 39);
    private static final int PANEL = Color.rgb(17, 35, 65);
    private static final int WHITE = Color.WHITE;
    private static final int BLUE = Color.rgb(0, 110, 243);
    private static final int PURPLE = Color.rgb(111, 55, 224);
    private static final int ORANGE = Color.rgb(243, 112, 29);
    private static final int GREEN = Color.rgb(0, 165, 106);
    private static final int PINK = Color.rgb(224, 48, 122);
    private static final int CYAN = Color.rgb(0, 150, 211);
    private final Map<Integer, List<Annotation>> annotations = new HashMap<>();

    private LinearLayout root;
    private LinearLayout content;
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private Uri openedUri;
    private int currentPage = 0;
    private int currentColor = Color.rgb(17, 103, 245);
    private Tool currentTool = Tool.NONE;
    private PageView pageView;
    private Exporter queuedExport;

    private enum Tool { NONE, PEN, HIGHLIGHT, TEXT }
    private interface Exporter { void write(OutputStream target) throws Exception; }
    private static class Annotation {
        Tool type;
        int color;
        float widthFraction;
        float x;
        float y;
        String text;
        List<PointF> points = new ArrayList<>();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        home();
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null) {
            openPdf(getIntent().getData());
        }
    }

    private int dp(float value) { return (int) (getResources().getDisplayMetrics().density * value + 0.5f); }
    private GradientDrawable background(int color, int corner) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(corner));
        return result;
    }
    private GradientDrawable gradient(int first, int second) {
        GradientDrawable result = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {first, second});
        result.setCornerRadius(dp(17));
        return result;
    }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(WHITE);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }
    private void addVerticalSpace(LinearLayout parent, int height) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void frame(String title, String subtitle) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));
        setContentView(root);
        TextView header = text(title, 25, true);
        header.setPadding(0, dp(5), 0, dp(3));
        root.addView(header);
        if (subtitle != null) {
            TextView sub = text(subtitle, 13, false);
            sub.setTextColor(Color.rgb(194, 207, 230));
            root.addView(sub);
        }
        addVerticalSpace(root, 12);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
    }
    private void card(LinearLayout parent, String title, String detail, int color, Runnable onClick) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(16), dp(16), dp(8), dp(15));
        block.setBackground(gradient(color, shade(color, 0.76f)));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(113), 1);
        layout.setMargins(dp(3), dp(4), dp(3), dp(4));
        parent.addView(block, layout);
        TextView name = text(title, 18, true);
        block.addView(name);
        addVerticalSpace(block, 9);
        TextView description = text(detail, 12, false);
        block.addView(description);
        block.setOnClickListener(v -> onClick.run());
    }
    private int shade(int color, float fraction) {
        return Color.rgb((int) (Color.red(color) * fraction), (int) (Color.green(color) * fraction),
                (int) (Color.blue(color) * fraction));
    }
    private void pair(String a, String detailA, int colorA, Runnable clickA,
                      String b, String detailB, int colorB, Runnable clickB) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(row);
        card(row, a, detailA, colorA, clickA);
        card(row, b, detailB, colorB, clickB);
    }
    private void action(LinearLayout parent, String title, int color, Runnable onClick) {
        TextView button = text(title, 15, true);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(16), dp(12), dp(10), dp(12));
        button.setBackground(background(color, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(4));
        parent.addView(button, params);
        button.setOnClickListener(v -> onClick.run());
    }
    private void home() {
        frame("PDF Útil Pro HS", "Editar  ·  Crear  ·  Organizar  ·  Compartir");
        pair("📂  Abrir PDF", "Desde tu celular", BLUE, this::choosePdf,
             "📄  Crear PDF", "Desde fotografías", Color.rgb(242, 58, 67), this::chooseImages);
        pair("✏  Editar PDF", "Texto, dibujos y resaltado", PURPLE, this::editPdf,
             "🖍  Anotar", "Subrayar y escribir", ORANGE, this::editPdf);
        pair("✍  Firmar", "Firma dibujada", GREEN, this::signPdf,
             "🗂  Organizar", "Unir y extraer páginas", CYAN, this::organize);
        pair("📷  Escanear", "Foto con la cámara", PINK, this::capturePhoto,
             "🔄  Convertir", "Imágenes a PDF", PURPLE, this::chooseImages);
        addVerticalSpace(content, 12);
        action(content, "📑  Documentos recientes", PANEL, this::openRecent);
        action(content, "⚙  Información y privacidad", PANEL, () -> showMessage(
           "PDF Útil Pro HS — versión beta. Los documentos se procesan en el celular. " +
           "Esta versión permite anotar, guardar copias, crear PDF desde fotos, unir y extraer páginas. " +
           "No modifica el texto original, no tiene OCR ni conversión Word."));
    }
    private void choosePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_OPEN);
    }
    private void chooseImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_IMAGES);
    }
    private void chooseMultiplePdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_MERGE);
    }
    private void capturePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            showMessage("No se encontró una aplicación de cámara.");
            return;
        }
        showMessage("El escáner beta toma una foto de resolución reducida. " +
           "Para escanear con mejor calidad, fotografía el documento con tu cámara normal y usa «Crear PDF». ");
        startActivityForResult(intent, REQUEST_CAMERA);
    }
    private void openRecent() {
        String value = getPreferences(MODE_PRIVATE).getString("recent_uri", "");
        if (value.isEmpty()) { showMessage("Aún no tienes documentos recientes."); return; }
        openPdf(Uri.parse(value));
    }
    private void openPdf(Uri picked) {
        try {
            closePdf();
            annotations.clear();
            descriptor = getContentResolver().openFileDescriptor(picked, "r");
            if (descriptor == null) throw new Exception("No se pudo obtener permiso para el documento.");
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) throw new Exception("El PDF no contiene páginas.");
            openedUri = picked;
            currentPage = 0;
            currentTool = Tool.NONE;
            try { getContentResolver().takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { /* Compartidos desde otra aplicación */ }
            getPreferences(MODE_PRIVATE).edit().putString("recent_uri", picked.toString()).apply();
            editor();
        } catch (Exception error) {
            closePdf();
            showMessage("No se pudo abrir este PDF: " + error.getMessage());
            home();
        }
    }
    private void editPdf() {
        if (renderer == null) choosePdf();
        else editor();
    }
    private void signPdf() {
        if (renderer == null) { choosePdf(); return; }
        currentTool = Tool.PEN;
        currentColor = Color.rgb(25, 70, 175);
        editor();
        showMessage("Firma simple: dibuja tu firma sobre la página y después pulsa «Guardar copia». " +
                "No equivale a una firma digital certificada.");
    }
    private void editor() {
        if (renderer == null) { home(); return; }
        frame("✏  Editar documento", "Página " + (currentPage + 1) + " de " + renderer.getPageCount());
        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        content.addView(toolsScroll);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        toolsScroll.addView(tools);
        toolbarButton(tools, "✎ Dibujar", () -> setTool(Tool.PEN));
        toolbarButton(tools, "▰ Resaltar", () -> setTool(Tool.HIGHLIGHT));
        toolbarButton(tools, "T Texto", () -> setTool(Tool.TEXT));
        toolbarButton(tools, "↶ Deshacer", this::undo);
        toolbarButton(tools, "🎨 Color", this::chooseColor);
        addVerticalSpace(content, 8);
        try (PdfRenderer.Page page = renderer.openPage(currentPage)) {
            Bitmap bitmap = renderBitmap(page, 1.8f);
            pageView = new PageView(bitmap);
            pageView.setBackground(background(WHITE, 4));
            content.addView(pageView, new LinearLayout.LayoutParams(-1, -2));
        } catch (Exception e) { showMessage("Error al mostrar página: " + e.getMessage()); }
        addVerticalSpace(content, 8);
        LinearLayout nav = new LinearLayout(this);
        content.addView(nav);
        navButton(nav, "◀ Anterior", () -> { if (currentPage > 0) { currentPage--; editor(); } });
        navButton(nav, "Siguiente ▶", () -> { if (currentPage < renderer.getPageCount() - 1) { currentPage++; editor(); } });
        action(content, "💾  Guardar copia PDF", BLUE, this::saveAnnotated);
        action(content, "↗  Compartir original", PANEL, this::shareOriginal);
        action(content, "🗂  Organizar páginas", PANEL, this::organize);
        action(content, "⌂  Volver al inicio", PANEL, this::home);
        TextView hint = text("Para agregar texto, selecciona «T Texto» y toca la página. " +
                "Los cambios se guardan en una copia nueva del PDF.", 12, false);
        hint.setTextColor(Color.rgb(184, 202, 224));
        content.addView(hint);
    }
    private void setTool(Tool tool) {
        currentTool = tool;
        Toast.makeText(this, "Herramienta: " + (tool == Tool.TEXT ? "Texto" : tool == Tool.PEN ? "Dibujar" : "Resaltar"), Toast.LENGTH_SHORT).show();
    }
    private void toolbarButton(LinearLayout bar, String title, Runnable onClick) {
        TextView button = text(title, 13, true);
        button.setPadding(dp(13), dp(13), dp(13), dp(13));
        button.setBackground(background(PANEL, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(5), 0);
        bar.addView(button, params);
        button.setOnClickListener(v -> onClick.run());
    }
    private void navButton(LinearLayout bar, String title, Runnable onClick) {
        TextView button = text(title, 14, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), dp(10), dp(6), dp(10));
        button.setBackground(background(PANEL, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(3), 0, dp(3), dp(5));
        bar.addView(button, params);
        button.setOnClickListener(v -> onClick.run());
    }
    private void chooseColor() {
        final String[] names = {"Azul", "Rojo", "Negro", "Verde", "Amarillo"};
        final int[] colors = {Color.rgb(17, 103, 245), Color.RED, Color.BLACK,
                Color.rgb(10, 145, 81), Color.rgb(245, 212, 0)};
        new AlertDialog.Builder(this).setTitle("Color de anotación").setItems(names,
                (dialog, which) -> currentColor = colors[which]).show();
    }
    private void undo() {
        List<Annotation> list = annotations.get(currentPage);
        if (list != null && !list.isEmpty()) {
            list.remove(list.size() - 1);
            if (pageView != null) pageView.invalidate();
        }
    }
    private List<Annotation> pageAnnotations(int index) {
        if (!annotations.containsKey(index)) annotations.put(index, new ArrayList<>());
        return annotations.get(index);
    }
    private void addText(float x, float y) {
        EditText field = new EditText(this);
        field.setSingleLine(false);
        field.setHint("Escribe tu texto...");
        new AlertDialog.Builder(this).setTitle("Agregar texto").setView(field)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Agregar", (dialog, which) -> {
                String value = field.getText().toString().trim();
                if (value.isEmpty()) return;
                Annotation note = new Annotation();
                note.type = Tool.TEXT;
                note.color = currentColor;
                note.x = x; note.y = y; note.text = value;
                pageAnnotations(currentPage).add(note);
                if (pageView != null) pageView.invalidate();
            }).show();
    }
    private void paintAnnotations(Canvas canvas, int index, int width, int height) {
        List<Annotation> items = annotations.get(index);
        if (items == null) return;
        for (Annotation item : items) {
            Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
            brush.setColor(item.color);
            if (item.type == Tool.TEXT) {
                brush.setTextSize(Math.max(13, width * 0.032f));
                brush.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                float x = item.x * width;
                float y = item.y * height;
                float step = brush.getTextSize() * 1.3f;
                for (String line : item.text.split("\\n", -1)) {
                    canvas.drawText(line, x, y, brush); y += step;
                }
                continue;
            }
            if (item.points.isEmpty()) continue;
            brush.setStyle(Paint.Style.STROKE);
            brush.setStrokeCap(Paint.Cap.ROUND);
            brush.setStrokeJoin(Paint.Join.ROUND);
            brush.setStrokeWidth(Math.max(2f, width * item.widthFraction));
            if (item.type == Tool.HIGHLIGHT) brush.setAlpha(105);
            Path path = new Path();
            PointF start = item.points.get(0);
            path.moveTo(start.x * width, start.y * height);
            if (item.points.size() == 1) path.lineTo(start.x * width + 0.1f, start.y * height);
            for (int j = 1; j < item.points.size(); j++) {
                PointF point = item.points.get(j);
                path.lineTo(point.x * width, point.y * height);
            }
            canvas.drawPath(path, brush);
        }
    }
    private class PageView extends View {
        Bitmap bitmap;
        Annotation active;
        PageView(Bitmap value) { super(MainActivity.this); bitmap = value; setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = View.MeasureSpec.getSize(widthMeasureSpec);
            int h = Math.max(dp(80), Math.round(w * bitmap.getHeight() / (float) bitmap.getWidth()));
            setMeasuredDimension(w, h);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(WHITE);
            canvas.drawBitmap(bitmap, null, new RectF(0, 0, getWidth(), getHeight()), new Paint(Paint.FILTER_BITMAP_FLAG));
            paintAnnotations(canvas, currentPage, getWidth(), getHeight());
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (currentTool == Tool.NONE) return false;
            float x = Math.max(0f, Math.min(1f, event.getX() / Math.max(1f, getWidth())));
            float y = Math.max(0f, Math.min(1f, event.getY() / Math.max(1f, getHeight())));
            if (currentTool == Tool.TEXT) {
                if (event.getAction() == MotionEvent.ACTION_UP) { addText(x, y); performClick(); }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                active = new Annotation();
                active.type = currentTool;
                active.color = currentColor;
                active.widthFraction = currentTool == Tool.HIGHLIGHT ? 0.025f : 0.0045f;
                active.points.add(new PointF(x, y));
                pageAnnotations(currentPage).add(active);
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && active != null) {
                active.points.add(new PointF(x, y));
                invalidate();
                return true;
            }
            if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) && active != null) {
                active.points.add(new PointF(x, y));
                active = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                performClick();
                return true;
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
    private Bitmap renderBitmap(PdfRenderer.Page page, float scale) {
        int width = Math.max(1, Math.round(page.getWidth() * scale));
        int height = Math.max(1, Math.round(page.getHeight() * scale));
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        output.eraseColor(WHITE);
        page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return output;
    }
    private void saveAnnotated() {
        if (renderer == null) { choosePdf(); return; }
        queueSave("PDF-Facil-Pro-editado.pdf", target -> {
            PdfDocument document = new PdfDocument();
            try {
                for (int i = 0; i < renderer.getPageCount(); i++) {
                    try (PdfRenderer.Page original = renderer.openPage(i)) {
                        Bitmap bitmap = renderBitmap(original, 1.8f);
                        Canvas bitmapCanvas = new Canvas(bitmap);
                        paintAnnotations(bitmapCanvas, i, bitmap.getWidth(), bitmap.getHeight());
                        appendPage(document, bitmap, original.getWidth(), original.getHeight());
                        bitmap.recycle();
                    }
                }
                document.writeTo(target);
            } finally { document.close(); }
        });
    }
    private void appendPage(PdfDocument pdf, Bitmap bitmap, int width, int height) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(width, height, pdf.getPages().size() + 1).create();
        PdfDocument.Page out = pdf.startPage(info);
        out.getCanvas().drawColor(WHITE);
        out.getCanvas().drawBitmap(bitmap, null, new RectF(0, 0, width, height), new Paint(Paint.FILTER_BITMAP_FLAG));
        pdf.finishPage(out);
    }
    private void queueSave(String defaultName, Exporter exporter) {
        queuedExport = exporter;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, defaultName);
        startActivityForResult(intent, REQUEST_SAVE);
    }
    private void shareOriginal() {
        if (openedUri == null) { choosePdf(); return; }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, openedUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Compartir PDF"));
    }
    private void organize() {
        frame("🗂  Organizar PDF", "Unir, extraer y reorganizar con copias nuevas");
        action(content, "➕  Unir varios documentos PDF", CYAN, this::chooseMultiplePdf);
        action(content, "📄  Extraer páginas del PDF abierto", PURPLE, this::askExtract);
        action(content, "↻  Girar todas las páginas 90°", ORANGE, this::rotateAll);
        action(content, "⌂  Volver al inicio", PANEL, this::home);
        TextView note = text("Nota: al unir o exportar páginas se crea un PDF nuevo a partir de imágenes de las páginas. " +
                "El texto puede perder su capacidad de selección.", 13, false);
        note.setTextColor(Color.rgb(195, 208, 231));
        content.addView(note);
    }
    private void askExtract() {
        if (renderer == null) { showMessage("Primero abre el PDF que quieres dividir o extraer."); return; }
        EditText field = new EditText(this);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        field.setText("1-" + renderer.getPageCount());
        new AlertDialog.Builder(this).setTitle("Indica rango de páginas (ejemplo 2-5)")
          .setView(field).setNegativeButton("Cancelar", null)
          .setPositiveButton("Extraer", (dialog, which) -> {
              String[] parts = field.getText().toString().trim().split("-");
              try {
                  int first = Integer.parseInt(parts[0].trim());
                  int last = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : first;
                  if (first < 1 || last < first || last > renderer.getPageCount()) throw new Exception("Rango fuera de límites");
                  queueSave("Paginas-" + first + "-" + last + ".pdf", target -> {
                      PdfDocument result = new PdfDocument();
                      try {
                          for (int i = first - 1; i < last; i++) {
                              try (PdfRenderer.Page page = renderer.openPage(i)) {
                                  Bitmap image = renderBitmap(page, 1.8f);
                                  Canvas canvas = new Canvas(image);
                                  paintAnnotations(canvas, i, image.getWidth(), image.getHeight());
                                  appendPage(result, image, page.getWidth(), page.getHeight());
                                  image.recycle();
                              }
                          }
                          result.writeTo(target);
                      } finally { result.close(); }
                  });
              } catch (Exception error) { showMessage("Escribe un rango válido, por ejemplo 1-3."); }
          }).show();
    }
    private void rotateAll() {
        if (renderer == null) { showMessage("Primero abre un PDF."); return; }
        queueSave("PDF-girado.pdf", target -> {
            PdfDocument result = new PdfDocument();
            try {
                for (int i = 0; i < renderer.getPageCount(); i++) {
                    try (PdfRenderer.Page p = renderer.openPage(i)) {
                        Bitmap source = renderBitmap(p, 1.5f);
                        Canvas imageCanvas = new Canvas(source);
                        paintAnnotations(imageCanvas, i, source.getWidth(), source.getHeight());
                        Bitmap rotated = Bitmap.createBitmap(source.getHeight(), source.getWidth(), Bitmap.Config.ARGB_8888);
                        Canvas c = new Canvas(rotated);
                        c.drawColor(WHITE);
                        c.translate(rotated.getWidth(), 0);
                        c.rotate(90);
                        c.drawBitmap(source, 0, 0, null);
                        appendPage(result, rotated, p.getHeight(), p.getWidth());
                        source.recycle(); rotated.recycle();
                    }
                }
                result.writeTo(target);
            } finally { result.close(); }
        });
    }
    private void mergeDocuments(List<Uri> uris) {
        if (uris.isEmpty()) { showMessage("Elige al menos un documento."); return; }
        queueSave("PDF-unidos.pdf", target -> {
            PdfDocument result = new PdfDocument();
            try {
                for (Uri documentUri : uris) {
                    try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(documentUri, "r")) {
                        if (fd == null) throw new Exception("Archivo sin acceso: " + documentUri);
                        try (PdfRenderer input = new PdfRenderer(fd)) {
                            for (int i = 0; i < input.getPageCount(); i++) {
                                try (PdfRenderer.Page page = input.openPage(i)) {
                                    Bitmap bitmap = renderBitmap(page, 1.5f);
                                    appendPage(result, bitmap, page.getWidth(), page.getHeight());
                                    bitmap.recycle();
                                }
                            }
                        }
                    }
                }
                result.writeTo(target);
            } finally { result.close(); }
        });
    }
    private Bitmap loadImage(Uri imageUri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(imageUri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        if (largest <= 0) throw new Exception("La imagen no es válida.");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (largest / options.inSampleSize > 2100) options.inSampleSize *= 2;
        try (InputStream stream = getContentResolver().openInputStream(imageUri)) {
            Bitmap image = BitmapFactory.decodeStream(stream, null, options);
            if (image == null) throw new Exception("No se pudo leer la imagen.");
            return image;
        }
    }
    private void imagePdf(List<Uri> images) {
        if (images.isEmpty()) { showMessage("No se seleccionaron imágenes."); return; }
        queueSave("Fotos-a-PDF.pdf", target -> {
            PdfDocument document = new PdfDocument();
            try {
                for (Uri uri : images) {
                    Bitmap image = loadImage(uri);
                    appendImageLetter(document, image);
                    image.recycle();
                }
                document.writeTo(target);
            } finally { document.close(); }
        });
    }
    private void appendImageLetter(PdfDocument result, Bitmap image) {
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(612, 792, result.getPages().size() + 1).create();
        PdfDocument.Page page = result.startPage(info);
        Canvas canvas = page.getCanvas();
        canvas.drawColor(WHITE);
        float factor = Math.min(564f / image.getWidth(), 744f / image.getHeight());
        float width = image.getWidth() * factor, height = image.getHeight() * factor;
        float x = (612 - width) / 2f, y = (792 - height) / 2f;
        canvas.drawBitmap(image, null, new RectF(x, y, x + width, y + height), new Paint(Paint.FILTER_BITMAP_FLAG));
        result.finishPage(page);
    }
    private void imageFromCamera(Bitmap image) {
        queueSave("Foto-escaneada.pdf", target -> {
            PdfDocument document = new PdfDocument();
            try { appendImageLetter(document, image); document.writeTo(target); }
            finally { document.close(); image.recycle(); }
        });
    }
    private List<Uri> pickUris(Intent result) {
        List<Uri> uris = new ArrayList<>();
        ClipData clip = result.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (result.getData() != null) uris.add(result.getData());
        return uris;
    }
    @Override protected void onActivityResult(int request, int status, Intent data) {
        super.onActivityResult(request, status, data);
        if (status != RESULT_OK) { if (request == REQUEST_SAVE) queuedExport = null; return; }
        if (request == REQUEST_OPEN && data != null && data.getData() != null) {
            openPdf(data.getData());
        } else if (request == REQUEST_IMAGES && data != null) {
            imagePdf(pickUris(data));
        } else if (request == REQUEST_MERGE && data != null) {
            mergeDocuments(pickUris(data));
        } else if (request == REQUEST_CAMERA && data != null && data.getExtras() != null) {
            Object photo = data.getExtras().get("data");
            if (photo instanceof Bitmap) imageFromCamera((Bitmap) photo);
            else showMessage("La cámara no devolvió una fotografía.");
        } else if (request == REQUEST_SAVE && data != null && data.getData() != null && queuedExport != null) {
            Exporter export = queuedExport;
            queuedExport = null;
            try (OutputStream target = getContentResolver().openOutputStream(data.getData())) {
                if (target == null) throw new Exception("No se pudo escribir el archivo.");
                export.write(target);
                target.flush();
                new AlertDialog.Builder(this).setTitle("Archivo guardado")
                   .setMessage("Tu PDF se guardó correctamente en la carpeta que elegiste.")
                   .setPositiveButton("Entendido", null).show();
            } catch (Exception error) {
                showMessage("No se pudo guardar el PDF: " + error.getMessage());
            }
        }
    }
    private void showMessage(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Aceptar", null).show();
    }
    private void closePdf() {
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) { }
        renderer = null;
        descriptor = null;
        openedUri = null;
    }
    @Override public void onBackPressed() { home(); }
    @Override protected void onDestroy() { closePdf(); super.onDestroy(); }
}
