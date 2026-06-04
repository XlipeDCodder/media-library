<?php

namespace Musicplayer\MediaLibrary\Facades;

use Illuminate\Support\Facades\Facade;

/**
 * @method static mixed execute(array $options = [])
 * @method static object|null getStatus()
 *
 * @see \Musicplayer\MediaLibrary\MediaLibrary
 */
class MediaLibrary extends Facade
{
    protected static function getFacadeAccessor(): string
    {
        return \Musicplayer\MediaLibrary\MediaLibrary::class;
    }
}