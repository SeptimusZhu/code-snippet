while (sendlen < len)
{
	ssize_t ret = send(fd, (void *)(buf + sendlen), (len - sendlen), 0);
	if (ret < 0 && (errno == EAGAIN || errno == EWOULDBLOCK))
	{
		continue;
	}
	else if (ret < 0 || errno == EPIPE)
	{
		log_warn("send failed, close connect");
		return -1;
	}
	else
	{
		sendlen += ret;
	}
{
