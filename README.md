# VirtualChest

A sponge plugin which provides virtual chest GUIs for menus like ChestCommands.  
一个用于提供诸如ChestCommands等插件提供了虚拟箱子GUI界面菜单的Sponge插件。

## Scope 适用范围

The plugin uses sponge api 5.0.0, so it is expected to work normally on spongevanilla/spongeforge 1.10.2-1.11.2 servers.  
该插件使用SpongeAPI 5.0.0，因此理论上应该可以在1.10.2-1.11.2版本的SpongeVanilla/SpongeForge服务端上正常工作。

If a bug was found, you can make an issue or a pull request.  
如果你发现了Bug，你可以提交一个Issue或者PullRequest。

## Feature & Configurations 功能配置

The feature description *to be added*, but the plugin itself provides [examples](resources/assets/virtualchest/examples) for reference.  
功能描述*待补充*，但插件本身提供了[示例文件](resources/assets/virtualchest/examples)可以参考。

You can find the examples in the `config/virtualchest/menu` directory which will be generated after you start the sponge server with this plugin for the first time.  
你可以在`config/virtualchest/menu`目录下找到会在第一次启动含有该插件的Sponge服务端启动后生成的示例文件。

All the `.conf` files in the `config/virtualchest/menu` directory determine all the available chest GUIs by default.  
默认情况下，所有`config/virtualchest/menu` 目录下的`.conf`文件决定了可用的箱子GUI列表。

## Unimplemented features 未实现的特性

* Command signs, I won't add this feature because the CommandSign plugin could do this <br> 命令牌子，不过由于CommandSign插件可以代劳，所以本人不准备添加
* Player operations as OP, I won't add this because of safety <br> 玩家以OP身份操作，由于安全性不予添加
* Support for Economy API, *work in progress* <br> 对经济API的支持，*正在填坑中*
* An api for developers, *also WIP* <br> 一套开发者API，*仍然正在填坑中*

## Commands & Permissions 命令权限

All the commands starts with `/virtualchest`, you can also use `/vchest` or `/vc` instead.  
所有的命令都以`/virtualchest`开头，你也可以使用诸如`/vchest`或`/vc`的简写代替。

#### /virtualchest reload <br> /virtualchest reload extract-examples
* Reload the plugin (you can use "/sponge plugins reload" to reload all the plugins including this one). If 'extract-examples' is specified, example chest GUI configurations will be extracted <br> 重新加载插件配置（你可以使用 /sponge plugins reload 命令重新加载所有插件的配置，包括这个插件）。如果设置了“extract-examples”选项，那么箱子GUI的示例配置文件将再次生成
* Permission 权限
    * virtualchest.reload

#### /virtualchest list
* List all the chest GUI names which are available <br> 列出所有可用的箱子GUI名称
* Permission 权限
    * virtualchest.list

#### /virtualchest open *\<name\>* <br> /virtualchest open *\<name\>* *\<player\>*
* Open the chest GUI whose *name* is specified if it is available. If the *player* is specified, open it to that player <br> 如果可用，打开指定名称（*name*）的箱子GUI。如果指定了玩家（*player*），则为该玩家打开箱子GUI
* Permission 权限
    * virtualchest.open.self
    * virtualchest.open.self.*name*
    * virtualchest.open.others
    * virtualchest.open.others.*name*

## License 协议授权

This plugin is licensed under [LGPL 3.0 license](LICENSE), and you can spread it to almost anywhere applicable to LGPL license without having to manually request for my agreement.  
该插件以[LGPL 3.0](LICENSE)授权，你可以不经过我的手动允许，将其传播到几乎任何地方，只要它不与LGPL协议相抵触。

There are only two places which need to be noticed: [mcbbs](http://www.mcbbs.net/) (a Minecraft forum in China) and [the sponge forum](https://forums.spongepowered.org/). The only reason is that I have already posted the plugin there, and the repost is not welcomed by forums.  
不过，有两个地方需要注意：[MCBBS](http://www.mcbbs.net/)和[Sponge官方论坛](https://forums.spongepowered.org/)。唯一的原因在于我已经把本插件在这两个地方发布了，而论坛是不欢迎二次发布的。

