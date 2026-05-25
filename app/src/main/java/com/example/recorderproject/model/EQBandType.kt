package com.example.recorderproject.model

enum class EQBandType(val displayName: String) {
    BELL("Bell"),
    LOW_SHELF("Lo Shelf"),
    HIGH_SHELF("Hi Shelf"),
    LOW_PASS("Lo Pass"),
    HIGH_PASS("Hi Pass"),
    LOW_CUT("Lo Cut"),
    HIGH_CUT("Hi Cut"),
    NOTCH("Notch"),
    BAND_PASS("Band Pass"),
    TILT("Tilt")
}
