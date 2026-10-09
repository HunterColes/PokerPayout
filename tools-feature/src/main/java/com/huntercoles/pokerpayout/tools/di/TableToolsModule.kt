package com.huntercoles.pokerpayout.tools.di

import com.huntercoles.pokerpayout.tools.table.BankTonight
import com.huntercoles.pokerpayout.tools.table.TonightSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TableToolsModule {
    /** The deal maker reads tonight's players and prizes from the Bank and the Payouts tab. */
    @Binds
    abstract fun bindTonightSource(source: BankTonight): TonightSource
}
