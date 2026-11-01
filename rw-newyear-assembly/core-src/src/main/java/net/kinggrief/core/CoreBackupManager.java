package net.kinggrief.core;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Manual snapshots of the Core data file only. Worlds and external plugins need separate backups. */
final class CoreBackupManager {
    private static final long MAX_SOURCE=128L*1024L*1024L;
    private static final int MAX_ARCHIVES=20;
    private static final String PREFIX="kgcore-";
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final KingGriefCore plugin;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,"KingGrief-backup-io");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    CoreBackupManager(KingGriefCore plugin){this.plugin=plugin;}
    void shutdown(){worker.shutdownNow();}
    void command(CommandSender sender,String[] args){
        if(args.length!=1||!("create".equalsIgnoreCase(args[0])||"list".equalsIgnoreCase(args[0]))){
            sender.sendMessage(ColorUtil.c("&e/kgbackup <create|list> &7— только данные KingGriefCore, без миров и других плагинов."));return;
        }
        if(!busy.compareAndSet(false,true)){sender.sendMessage(ColorUtil.c("&eДругая операция резервного копирования уже выполняется."));return;}
        if("list".equalsIgnoreCase(args[0])){list(sender);return;}
        Path source=null;
        try{
            Path folder=backupDirectory();Path data=plugin.getDataFolder().toPath().resolve("data.yml");
            // Flush the in-memory Core state before creating an immutable hard-linked snapshot.
            if(!plugin.getUtilities().checkpointForBackup())throw new IOException("данные ядра не удалось сохранить");
            if(Files.isSymbolicLink(data)||!Files.isRegularFile(data,LinkOption.NOFOLLOW_LINKS))throw new IOException("data.yml не является обычным файлом");
            long size=Files.size(data);if(size>MAX_SOURCE)throw new IOException("data.yml больше 128 МиБ");
            String id=UUID.randomUUID().toString();source=folder.resolve(".kgbackup-source-"+id);
            // DataStore replaces data.yml atomically rather than editing its inode. This link
            // therefore fixes the version without holding the live file open for the ZIP worker.
            Files.createLink(source,data);
            Path frozen=source;
            worker.execute(()->create(sender,folder,frozen,id));
            sender.sendMessage(ColorUtil.c("&eСнимок ядра создан; архивирование идёт в фоне. Миры и данные других плагинов не включены."));
        }catch(Exception error){
            if(source!=null)try{Files.deleteIfExists(source);}catch(IOException ignored){}
            busy.set(false);sender.sendMessage(ColorUtil.c("&cКопия не создана: "+safeMessage(error)+". Исходные данные не изменены."));
        }
    }
    private Path backupDirectory() throws IOException {
        Path root=plugin.getDataFolder().toPath();
        if(Files.isSymbolicLink(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("небезопасная папка KingGriefCore");
        Path folder=root.resolve("backups");
        if(Files.isSymbolicLink(folder))throw new IOException("папка backups является ссылкой");
        if(!Files.exists(folder,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(folder);
        if(!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))throw new IOException("backups не является папкой");
        try{Files.setPosixFilePermissions(folder,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));}
        catch(UnsupportedOperationException ignored){/* non-POSIX host; protect the folder using host permissions */}
        return folder;
    }
    private void list(CommandSender sender){
        try{
            Path folder=backupDirectory();
            worker.execute(()->{
                try{
                    List<String> names=archives(folder);Collections.sort(names,Collections.reverseOrder());
                    List<String> lines=new ArrayList<String>();lines.add("&#80D8FFКопии Core ("+names.size()+"/"+MAX_ARCHIVES+"):");
                    for(int i=0;i<Math.min(10,names.size());i++)lines.add("&7• &f"+names.get(i));
                    if(names.isEmpty())lines.add("&7Копий пока нет.");
                    lines.add("&8Миры, инвентари, PlayerPoints и LuckPerms в эти копии не входят.");
                    reply(sender,lines.toArray(new String[0]));
                }catch(Exception error){reply(sender,"&cНе удалось прочитать список копий: "+safeMessage(error));}
                finally{busy.set(false);}
            });
        }catch(Exception error){busy.set(false);sender.sendMessage(ColorUtil.c("&cНе удалось открыть папку копий: "+safeMessage(error)));}
    }
    private static List<String> archives(Path folder) throws IOException {
        List<String> names=new ArrayList<String>();
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(folder,PREFIX+"*.zip")){
            for(Path path:paths){
                String name=path.getFileName().toString();
                if(name.matches("kgcore-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}\\.zip")&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))names.add(name);
            }
        }
        return names;
    }
    private void create(CommandSender sender,Path folder,Path source,String id){
        Path temp=null;
        try{
            if(archives(folder).size()>=MAX_ARCHIVES)throw new IOException("достигнут предел в 20 копий; перенесите старые архивы вручную");
            temp=Files.createTempFile(folder,".kgbackup-",".part");
            try{Files.setPosixFilePermissions(temp,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));}
            catch(UnsupportedOperationException ignored){}
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(OutputStream raw=Files.newOutputStream(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
                ZipOutputStream zip=new ZipOutputStream(raw)){
                zip.putNextEntry(new ZipEntry("data.yml"));
                try(InputStream in=Files.newInputStream(source,StandardOpenOption.READ)){
                    byte[] block=new byte[32768];int read;
                    while((read=in.read(block))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new IOException("архивирование прервано");
                        size+=read;if(size>MAX_SOURCE)throw new IOException("data.yml больше 128 МиБ");
                        digest.update(block,0,read);zip.write(block,0,read);
                    }
                }
                zip.closeEntry();
                String sha=hex(digest.digest());
                entry(zip,"MANIFEST.txt","KingGriefCore data.yml snapshot\nUTC: "+Instant.now()+"\nSize: "+size+" bytes\nSHA-256: "+sha+"\n");
                entry(zip,"README.txt","Contains ONLY plugins/KingGriefCore/data.yml.\nNOT INCLUDED: worlds, player inventories and ender chests, other plugins, LuckPerms, PlayerPoints, replay files, configuration and resource packs.\nKeep this file private: it contains player and economy data. Copy it off the server.\nRestore only with the server stopped, after creating a full independent backup.\n");
            }
            try(FileChannel file=FileChannel.open(temp,StandardOpenOption.WRITE)){file.force(true);}
            // Re-read the archive and CRC before reporting success. Hash is checked against the frozen inode.
            verify(temp,source);
            String name=PREFIX+DATE.format(Instant.now())+"-"+id.substring(0,8)+".zip";
            Path output=folder.resolve(name);
            // A new hard link publishes the complete inode atomically and fails if
            // a file already occupies the name; no existing archive is replaced.
            Files.createLink(output,temp);
            try{Files.delete(temp);temp=null;}
            catch(IOException cleanup){plugin.getLogger().warning("[CoreBackup] Complete archive published, but temporary link remains: "+cleanup.getMessage());}
            try(FileChannel dir=FileChannel.open(folder,StandardOpenOption.READ)){dir.force(true);}
            catch(Exception ignored){/* not all filesystems support directory fsync */}
            reply(sender,"&#00E676Копия Core готова: &fplugins/KingGriefCore/backups/"+name,
                    "&eЭто НЕ копия миров, инвентарей и других плагинов. Храните её приватно и отдельно от сервера.");
        }catch(Exception error){plugin.getLogger().warning("[CoreBackup] Backup not created: "+error);reply(sender,"&cКопия Core не создана: "+safeMessage(error));}
        finally{
            if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException error){plugin.getLogger().warning("[CoreBackup] Could not remove partial archive: "+error.getMessage());}
            try{Files.deleteIfExists(source);}catch(IOException error){plugin.getLogger().warning("[CoreBackup] Could not remove temporary snapshot: "+error.getMessage());}
            busy.set(false);
        }
    }
    private static void verify(Path zip,Path source) throws Exception {
        MessageDigest expected=MessageDigest.getInstance("SHA-256"),actual=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(source)){hash(in,expected);}
        try(ZipFile archive=new ZipFile(zip.toFile())){
            ZipEntry entry=archive.getEntry("data.yml");
            if(entry==null||archive.getEntry("MANIFEST.txt")==null||archive.getEntry("README.txt")==null)throw new IOException("неполный архив");
            try(InputStream in=archive.getInputStream(entry)){hash(in,actual);}
        }
        if(!MessageDigest.isEqual(expected.digest(),actual.digest()))throw new IOException("контрольная сумма архива не совпала");
    }
    private static void hash(InputStream in,MessageDigest digest) throws IOException {
        byte[] block=new byte[32768];long count=0;int n;
        while((n=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("проверка прервана");count+=n;if(count>MAX_SOURCE)throw new IOException("слишком большой файл");digest.update(block,0,n);}
    }
    private static void entry(ZipOutputStream zip,String name,String text) throws IOException {zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();}
    private static String safeMessage(Exception error){String m=error.getMessage();return m==null?error.getClass().getSimpleName():m.replace('\n',' ').replace('\r',' ');}
    private void reply(CommandSender sender,String... lines){
        try{Bukkit.getScheduler().runTask(plugin,()->{for(String line:lines)sender.sendMessage(ColorUtil.c(line));});}
        catch(IllegalStateException disabled){plugin.getLogger().info("[CoreBackup] "+String.join(" ",lines));}
    }
}
