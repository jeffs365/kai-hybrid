package com.inspiredandroid.kai.data

actual fun createReminderDelivery(settings: AppSettings, tasks: TaskStore): ReminderDelivery = UnsupportedReminderDelivery()
