package com.transiva.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

final class MerchantPosPrintAdapter extends PrintDocumentAdapter {
    private final Context context;
    private final String receipt;
    private PrintAttributes attributes;
    private StaticLayout layout;
    private final List<Integer> boundaries=new ArrayList<>();

    MerchantPosPrintAdapter(Context context,List<String> receipt) {
        this.context=context;
        this.receipt=String.join("\n",receipt);
    }

    @Override public void onLayout(PrintAttributes oldAttributes,PrintAttributes newAttributes,CancellationSignal signal,
                                   LayoutResultCallback callback,Bundle extras) {
        if (signal.isCanceled()) {callback.onLayoutCancelled();return;}
        attributes=newAttributes;
        Rect area;
        PrintedPdfDocument layoutDocument=new PrintedPdfDocument(context,newAttributes);
        try {area=layoutDocument.getPageContentRect();}
        finally {layoutDocument.close();}
        if (area.width()<40 || area.height()<40) {layout=null;callback.onLayoutFailed("Area cetak terlalu kecil.");return;}
        TextPaint paint=new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);paint.setTextSize(11);
        layout=StaticLayout.Builder.obtain(receipt,0,receipt.length(),paint,area.width())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3,1).build();
        boundaries.clear();boundaries.add(0);
        int start=0;
        for (int line=0;line<layout.getLineCount();line++) {
            if (layout.getLineBottom(line)-layout.getLineTop(start)>area.height() && line>start) {
                boundaries.add(line);start=line;
            }
        }
        boundaries.add(layout.getLineCount());
        callback.onLayoutFinished(new PrintDocumentInfo.Builder("struk-transiva.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(boundaries.size()-1).build(),
                !newAttributes.equals(oldAttributes));
    }

    @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,CancellationSignal signal,WriteResultCallback callback) {
        if (attributes==null || layout==null) {callback.onWriteFailed("Struk belum siap.");return;}
        List<PageRange> written=new ArrayList<>();
        PrintedPdfDocument document=new PrintedPdfDocument(context,attributes);
        try (FileOutputStream output=new FileOutputStream(destination.getFileDescriptor())) {
            for (int i=0;i<boundaries.size()-1;i++) {
                if (signal.isCanceled()) {callback.onWriteCancelled();return;}
                boolean requested=false;
                for (PageRange range:pages) if (i>=range.getStart() && i<=range.getEnd()) {requested=true;break;}
                if (!requested) continue;
                android.graphics.pdf.PdfDocument.Page page=document.startPage(i);
                Rect area=page.getInfo().getContentRect();
                int top=layout.getLineTop(boundaries.get(i));
                int bottom=layout.getLineBottom(boundaries.get(i+1)-1);
                page.getCanvas().save();
                // PrintedPdfDocument positions the canvas at the printable content origin.
                page.getCanvas().clipRect(0,0,area.width(),bottom-top);
                page.getCanvas().translate(0,-top);
                layout.draw(page.getCanvas());page.getCanvas().restore();
                document.finishPage(page);written.add(new PageRange(i,i));
            }
            document.writeTo(output);
            callback.onWriteFinished(written.toArray(new PageRange[0]));
        } catch (Exception error) {callback.onWriteFailed("Struk belum dapat dicetak.");}
        finally {document.close();}
    }

}
