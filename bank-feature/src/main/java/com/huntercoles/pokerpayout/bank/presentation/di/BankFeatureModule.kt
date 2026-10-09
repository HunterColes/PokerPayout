package com.huntercoles.pokerpayout.bank.presentation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.huntercoles.pokerpayout.bank.presentation.BankNavigationFactory
import com.huntercoles.pokerpayout.bank.presentation.composable.BankTableKnockouts
import com.huntercoles.pokerpayout.core.navigation.NavigationFactory
import com.huntercoles.pokerpayout.core.presentation.TableKnockouts
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface BankFeatureModule {

    @Singleton
    @Binds
    @IntoSet
    fun bindBankNavigationFactory(factory: BankNavigationFactory): NavigationFactory

    /** PP-135: the Bank's knockout on the full-screen clock. */
    @Binds
    fun bindTableKnockouts(knockouts: BankTableKnockouts): TableKnockouts
}
