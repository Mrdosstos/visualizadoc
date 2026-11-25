package br.com.mrdosstos.visualizadoc

import android.app.Application
import br.com.mrdosstos.visualizadoc.planilha.PoiAndroid

class VisualizaDocApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PoiAndroid.configurar()
    }
}
