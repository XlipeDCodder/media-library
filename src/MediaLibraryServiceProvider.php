<?php

namespace Musicplayer\MediaLibrary;

use Illuminate\Support\ServiceProvider;
use Musicplayer\MediaLibrary\Commands\CopyAssetsCommand;

class MediaLibraryServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(MediaLibrary::class, function () {
            return new MediaLibrary();
        });
    }

    public function boot(): void
    {
        // Register plugin hook commands
        if ($this->app->runningInConsole()) {
            $this->commands([
                CopyAssetsCommand::class,
            ]);
        }
    }
}