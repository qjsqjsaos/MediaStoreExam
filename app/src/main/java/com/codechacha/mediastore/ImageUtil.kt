package com.codechacha.mediastore

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix


class ImageUtil {
    companion object {
        fun overlay(bmp1: Bitmap, bmp2: Bitmap): Bitmap? {
            val bmOverlay = Bitmap.createBitmap(bmp1.width, bmp1.height, bmp1.config)
            val canvas = Canvas(bmOverlay)
            canvas.drawBitmap(bmp1, Matrix(), null)
            canvas.drawBitmap(bmp2, Matrix(), null)
            return bmOverlay
        }


    }
}