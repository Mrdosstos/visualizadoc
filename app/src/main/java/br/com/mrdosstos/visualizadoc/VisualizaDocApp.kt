package br.com.mrdosstos.visualizadoc

import android.app.Application
import br.com.mrdosstos.visualizadoc.planilha.PoiAndroid
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class VisualizaDocApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PoiAndroid.configurar()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
