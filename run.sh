
dir=$(dirname "$0")
#cd "$dir"

for jar in "$dir/lib/"*.jar
do
	cp="${cp}${jar}:"
done

cp="${cp}${dir}/bin"
echo "classpath: ${cp}"

java -Djsse.enableSNIExtension=false -Djava.util.logging.config.file=logging.properties -cp "${cp}"  net_maclife_wechat_http_BotApp
