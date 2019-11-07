package Geral;

import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.Arrays;
  
public class Servidor { 
    static final int CABECALHO = 4;
    static final int TAMANHO_PACOTE = 1000 + CABECALHO;
    static final int PORTA_SERVIDOR = 8000;
    static final int PORTA_ACK = 9000;
 
    public Servidor(int portaEntrada, int portaDestino, String caminho) {
        DatagramSocket socketEntrada, socketSaida;
        System.out.println("Servidor: porta de entrada: " + portaEntrada + ", " + "porta de destino: " + portaDestino + ".");
        int ultimoNum = -1;
        int proxNum = 0;  
        boolean transferenciaCompleta = false;  
 
      
        try {
            socketEntrada = new DatagramSocket(portaEntrada);
            socketSaida = new DatagramSocket();
            System.out.println("Servidor Conectado...");
            try {
                byte[] recebeDados = new byte[TAMANHO_PACOTE];
                DatagramPacket recebePacote = new DatagramPacket(recebeDados, recebeDados.length);
 
                FileOutputStream fos = null;
 
                while (!transferenciaCompleta) {
                    int i = 0;
                    socketEntrada.receive(recebePacote);
                    InetAddress enderecoIP = recebePacote.getAddress();
 
                    int numSeq = ByteBuffer.wrap(Arrays.copyOfRange(recebeDados, 0, CABECALHO)).getInt();
                    System.out.println("Servidor: Numero de sequencia recebido " + numSeq);
 
                    
                    if (numSeq == proxNum) {
                       
                        if (recebePacote.getLength() == CABECALHO) {
                            byte[] pacoteAck = gerarPacote(-2);     
                            socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                            transferenciaCompleta = true;
                            System.out.println("Servidor: Todos pacotes foram recebidos! Arquivo criado!");
                        } else {
                            proxNum = numSeq + TAMANHO_PACOTE - CABECALHO; 
                            byte[] pacoteAck = gerarPacote(proxNum);
                                socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                                System.out.println("Servidor: Ack enviado " + proxNum);
                          }
 
                        
                        if (numSeq == 0 && ultimoNum == -1) {
                               
                            File arquivo = new File(caminho);
                            if (!arquivo.exists()) {
                                arquivo.createNewFile();
                            }
                            fos = new FileOutputStream(arquivo);
                        }
                        
                        fos.write(recebeDados, CABECALHO, recebePacote.getLength() - CABECALHO);
 
                        ultimoNum = numSeq; 
                    } else {    
                        byte[] pacoteAck = gerarPacote(ultimoNum);
                        socketSaida.send(new DatagramPacket(pacoteAck, pacoteAck.length, enderecoIP, portaDestino));
                        System.out.println("Servidor: arquivo duplicado " + ultimoNum);
                    }
 
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            } finally {
                socketEntrada.close();
                socketSaida.close();
                System.out.println("Servidor: Socket de entrada fechado!");
                System.out.println("Servidor: Socket de saida fechado!");
            }
        } catch (SocketException e1) {
            e1.printStackTrace();
        }
    }
    
    public byte[] gerarPacote(int numAck) {
        byte[] numAckBytes = ByteBuffer.allocate(CABECALHO).putInt(numAck).array();
        ByteBuffer bufferPacote = ByteBuffer.allocate(CABECALHO);
        bufferPacote.put(numAckBytes);
        return bufferPacote.array();
    }
 
    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);
        System.out.print("Digite o diretorio do arquivo a ser criado: ");
        String diretorio = teclado.nextLine();
        System.out.print("Digite o nome do arquivo a ser criado: ");
        String nome = teclado.nextLine();
 
        Servidor servidor = new Servidor(PORTA_SERVIDOR, PORTA_ACK, diretorio + nome);
    }
}