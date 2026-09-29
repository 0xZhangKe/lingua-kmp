package com.zhangke.lingua

class LinguaNative private constructor(){

    companion object{

        init {
            System.loadLibrary("lingua_kmp")
        }

        @JvmStatic
        external fun detect(
            text: String,
        ): String?
    }
}
